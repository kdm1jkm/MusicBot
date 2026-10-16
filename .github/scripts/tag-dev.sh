#!/usr/bin/env bash
set -euo pipefail

# These read-only helpers are also used by tag-dev.yml to avoid rechecking jobs
# for commits that already have the correct beta tag.
tag_dev_refresh_refs() {
  git fetch --prune --no-tags origin \
    '+refs/heads/dev:refs/remotes/origin/dev' \
    '+refs/tags/*:refs/tag-dev/origin/*'
}

tag_dev_base_version() {
  local sha=$1 properties line base='' count=0
  local property_start='^[[:space:]]*version([[:space:]]*[:=]|[[:space:]])'
  local property_value='^[[:space:]]*version([[:space:]]*[:=][[:space:]]*|[[:space:]]+)([^[:space:]]+)[[:space:]]*$'
  if ! properties=$(git show "${sha}:gradle.properties"); then
    printf 'Cannot read gradle.properties at %s.\n' "$sha" >&2
    return 2
  fi
  while IFS= read -r line || [[ -n "$line" ]]; do
    line=${line%$'\r'}
    if [[ "$line" =~ $property_start ]]; then
      count=$((count + 1))
      if [[ ! "$line" =~ $property_value ]]; then
        printf 'Invalid version property at %s.\n' "$sha" >&2
        return 2
      fi
      base=${BASH_REMATCH[2]}
    fi
  done <<< "$properties"
  if [[ "$count" != 1 || ! "$base" =~ ^[0-9]{4}\.[0-9]+\.[0-9]+$ ]]; then
    printf 'Expected one YYMM.major.minor version at %s.\n' "$sha" >&2
    return 2
  fi
  case "${base:2:2}" in
    0[1-9]|1[0-2]) ;;
    *) printf 'Invalid YYMM month in version %s.\n' "$base" >&2; return 2 ;;
  esac
  printf '%s\n' "$base"
}

# Return 0 with a matching tag, 1 when untagged, and 2 on a Git error.
tag_dev_existing_tag() {
  local sha=$1 base=$2 tags tag suffix
  if ! tags=$(git for-each-ref --points-at="$sha" \
    --format='%(refname:strip=3)' refs/tag-dev/origin/); then
    return 2
  fi
  while IFS= read -r tag; do
    [[ "$tag" == "v${base}.beta-"* ]] || continue
    suffix=${tag#"v${base}.beta-"}
    if [[ "$suffix" =~ ^[1-9][0-9]*$ ]]; then
      printf '%s\n' "$tag"
      return 0
    fi
  done <<< "$tags"
  return 1
}

tag_dev_main() {
  if [[ $# != 1 || ! "$1" =~ ^[0-9a-fA-F]{40}$ ]]; then
    printf 'Usage: bash .github/scripts/tag-dev.sh <full-commit-sha>\n' >&2
    return 2
  fi
  local sha base tags tag suffix target existing='' largest=0 next index digit carry
  tag_dev_refresh_refs || return 2
  sha=$(git rev-parse --verify "${1}^{commit}") || return 2
  if ! git merge-base --is-ancestor "$sha" refs/remotes/origin/dev; then
    printf '%s is not an ancestor of origin/dev; refusing to tag.\n' "$sha" >&2
    return 2
  fi
  base=$(tag_dev_base_version "$sha") || return 2
  tags=$(git for-each-ref --format='%(refname:strip=3)' refs/tag-dev/origin/) || return 2
  while IFS= read -r tag; do
    [[ "$tag" == "v${base}.beta-"* ]] || continue
    suffix=${tag#"v${base}.beta-"}
    [[ "$suffix" =~ ^[1-9][0-9]*$ ]] || continue
    if ! target=$(git rev-parse --verify "refs/tag-dev/origin/${tag}^{commit}"); then
      printf 'Existing beta tag %s does not resolve to a commit.\n' "$tag" >&2
      return 2
    fi
    [[ "$target" != "$sha" ]] || existing=$tag
    # Compare decimal strings without overflowing Bash's integer range.
    if [[ ${#suffix} -gt ${#largest} || ( ${#suffix} -eq ${#largest} && "$suffix" > "$largest" ) ]]; then
      largest=$suffix
    fi
  done <<< "$tags"
  if [[ -n "$existing" ]]; then
    printf '%s already has %s; no tag created.\n' "$sha" "$existing"
    return 0
  fi

  # Increment the numeric suffix as decimal digits, including beyond 9.
  next=$largest
  carry=1
  for ((index=${#next} - 1; index >= 0; index--)); do
    digit=${next:index:1}
    if [[ "$digit" == 9 ]]; then
      next="${next:0:index}0${next:index+1}"
    else
      next="${next:0:index}$((digit + 1))${next:index+1}"
      carry=0
      break
    fi
  done
  if [[ "$carry" == 1 ]]; then
    next="1${next}"
  fi
  tag="v${base}.beta-${next}"
  # A direct, non-force push creates an immutable lightweight tag, not a commit.
  # Do not treat a competing publisher's rejection as a successful no-op.
  if ! git push origin "${sha}:refs/tags/${tag}"; then
    printf 'Failed to publish immutable tag %s (push conflict or remote error). Re-run to refresh remote tags; no tag was overwritten.\n' "$tag" >&2
    return 1
  fi
  printf 'Published %s at %s.\n' "$tag" "$sha"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  tag_dev_main "$@"
fi
