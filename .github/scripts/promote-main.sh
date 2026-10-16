#!/usr/bin/env bash
# CI is checked by promote-main.yml; this script enforces the Git promotion contract.
set -euo pipefail

fail() {
  printf '오류: %s\n' "$*" >&2
  exit 1
}

[[ $# -eq 2 ]] || fail '사용법: bash .github/scripts/promote-main.sh VERSION EXPECTED_DEV_SHA'
version=$1
expected_dev_sha=${2,,}
[[ $version =~ ^[0-9]{4}\.[0-9]+\.[0-9]+$ ]] || fail '버전은 YYMM.major.minor 형식이어야 합니다.'
[[ ${version:2:2} =~ ^(0[1-9]|1[0-2])$ ]] || fail '버전의 월은 01~12여야 합니다.'
[[ $expected_dev_sha =~ ^[0-9a-f]{40}$ ]] || fail '검토한 dev SHA 40자 전체가 필요합니다.'
[[ -z $(git status --porcelain) ]] || fail '작업 트리가 깨끗해야 합니다.'

tag="v$version"
merge_message="chore: promote dev $expected_dev_sha to main as $tag"
tag_message="Release $tag from dev $expected_dev_sha"

# Never force-fetch tags: a conflicting local tag is also an error.
git fetch --no-tags origin \
  '+refs/heads/dev:refs/remotes/origin/dev' \
  '+refs/heads/main:refs/remotes/origin/main' \
  'refs/tags/*:refs/tags/*'
[[ $(git rev-parse refs/remotes/origin/dev) == "$expected_dev_sha" ]] \
  || fail 'dev HEAD가 검토한 SHA에서 변경되었습니다.'

properties=$(git show "$expected_dev_sha:gradle.properties")
property_version=''
version_count=0
while IFS= read -r line || [[ -n $line ]]; do
  line=${line%$'\r'}
  if [[ $line =~ ^[[:space:]]*version[[:space:]]*=[[:space:]]*([^[:space:]]+)[[:space:]]*$ ]]; then
    property_version=${BASH_REMATCH[1]}
    version_count=$((version_count + 1))
  fi
done <<< "$properties"
[[ $version_count -eq 1 && $property_version == "$version" ]] \
  || fail '입력 버전이 dev의 gradle.properties version과 일치하지 않습니다.'

# Only an actual origin tag counts as beta evidence; stale local tags do not.
beta_tags=$(git ls-remote --tags origin "refs/tags/v${version}.beta-*")
has_beta=false
while read -r object ref; do
  candidate=${ref#refs/tags/}
  if [[ $candidate =~ ^v[0-9]{4}\.[0-9]+\.[0-9]+\.beta-[1-9][0-9]*$ ]] \
      && [[ $(git rev-parse "refs/tags/$candidate") == "$object" ]] \
      && [[ $(git rev-parse "refs/tags/$candidate^{commit}") == "$expected_dev_sha" ]]; then
    has_beta=true
    break
  fi
done <<< "$beta_tags"
[[ $has_beta == true ]] || fail '동일한 dev SHA에 해당 버전의 beta 태그가 없습니다.'

remote_tag=$(git ls-remote --tags origin "refs/tags/$tag")
if [[ -n $remote_tag ]]; then
  # A rerun is a no-op only for the exact annotated, two-parent promotion
  # already at origin/main. Anything else is an immutable version collision.
  [[ $(git cat-file -t "refs/tags/$tag") == tag ]] || fail '이미 존재하는 버전 태그가 승격 태그가 아닙니다.'
  [[ $(git rev-parse "refs/tags/$tag") == "${remote_tag%%$'\t'*}" ]] || fail '로컬/원격 버전 태그가 다릅니다.'
  promoted_sha=$(git rev-parse "refs/tags/$tag^{commit}")
  [[ $promoted_sha == "$(git rev-parse refs/remotes/origin/main)" ]] || fail '버전 태그가 이미 다른 승격에 사용되었습니다.'
  read -r -a parents <<< "$(git show -s --format=%P "$promoted_sha")"
  [[ ${#parents[@]} -eq 2 && ${parents[1]} == "$expected_dev_sha" ]] || fail '기존 태그의 병합 부모가 일치하지 않습니다.'
  [[ $(git show -s --format=%B "$promoted_sha") == "$merge_message" ]] || fail '기존 태그의 승격 커밋이 일치하지 않습니다.'
  [[ $(git for-each-ref --format='%(contents)' "refs/tags/$tag") == "$tag_message" ]] || fail '기존 태그의 승격 정보가 일치하지 않습니다.'
  printf '이미 완료된 승격입니다: %s (%s)\n' "$tag" "$promoted_sha"
  exit 0
fi
if git show-ref --verify --quiet "refs/tags/$tag"; then
  fail '동일한 로컬 버전 태그가 이미 존재합니다.'
fi
if git merge-base --is-ancestor "$expected_dev_sha" refs/remotes/origin/main; then
  fail '검토한 dev 커밋은 이미 main에 포함되어 있습니다.'
fi

# Start from main, not dev, so every promotion preserves the main first-parent
# history. main need not be an ancestor of dev after an earlier promotion.
git checkout --detach refs/remotes/origin/main
git merge --no-ff --no-edit -m "$merge_message" refs/remotes/origin/dev
read -r -a parents <<< "$(git show -s --format=%P HEAD)"
[[ ${#parents[@]} -eq 2 && ${parents[1]} == "$expected_dev_sha" ]] || fail '승격은 dev를 두 번째 부모로 하는 병합 커밋이어야 합니다.'
git tag -a "$tag" -m "$tag_message"

# Recheck the reviewed tip immediately before the atomic, non-force update.
remote_dev=$(git ls-remote --heads origin refs/heads/dev)
[[ ${remote_dev%%$'\t'*} == "$expected_dev_sha" ]] || fail '승격 준비 중 dev HEAD가 변경되었습니다.'
git push --atomic origin HEAD:refs/heads/main "refs/tags/$tag"
printf '승격 완료: %s (%s)\n' "$tag" "$(git rev-parse HEAD)"
