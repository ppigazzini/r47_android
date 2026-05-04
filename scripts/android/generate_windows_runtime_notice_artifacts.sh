#!/bin/bash

set -Eeuo pipefail

usage() {
    cat <<'EOF'
Usage:
    scripts/android/generate_windows_runtime_notice_artifacts.sh \
    --package-dir <path> \
    [--android-source-repository-url <url>] \
    [--android-source-commit <commit>] \
    [--upstream-source-repository-url <url>] \
    [--upstream-source-commit <commit>] \
    [--xlsxio-source-repository-url <url>] \
    [--xlsxio-source-commit <commit>]
EOF
}

package_dir=""
android_source_repository_url=""
android_source_commit=""
upstream_source_repository_url=""
upstream_source_commit=""
xlsxio_source_repository_url=""
xlsxio_source_commit=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --package-dir)
            package_dir="$2"
            shift 2
            ;;
        --android-source-repository-url)
            android_source_repository_url="$2"
            shift 2
            ;;
        --android-source-commit)
            android_source_commit="$2"
            shift 2
            ;;
        --upstream-source-repository-url)
            upstream_source_repository_url="$2"
            shift 2
            ;;
        --upstream-source-commit)
            upstream_source_commit="$2"
            shift 2
            ;;
        --xlsxio-source-repository-url)
            xlsxio_source_repository_url="$2"
            shift 2
            ;;
        --xlsxio-source-commit)
            xlsxio_source_commit="$2"
            shift 2
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            echo "Unknown argument: $1" >&2
            usage >&2
            exit 1
            ;;
    esac
done

if [[ -z "$package_dir" ]]; then
    echo "Missing required --package-dir argument." >&2
    usage >&2
    exit 1
fi

if [[ ! -d "$package_dir" ]]; then
    echo "Package directory not found: $package_dir" >&2
    exit 1
fi

if ! command -v pacman >/dev/null 2>&1; then
    echo "This script requires pacman to resolve bundled DLL owners and license directories." >&2
    exit 1
fi

notice_root="$package_dir/repo-notices/windows"
licenses_dir="$notice_root/licenses"
inventory_file="$notice_root/WINDOWS-RUNTIME-DLLS.txt"
summary_file="$notice_root/WINDOWS-DLL-NOTICES.txt"
spdx_file="$package_dir/THIRD-PARTY.spdx.json"

tmp_root="${TMPDIR:-$(dirname "$package_dir")}"
mkdir -p "$tmp_root"
tmp_dir="$(mktemp -d "$tmp_root/r47-windows-notices.XXXXXX")"
cleanup() {
    rm -rf "$tmp_dir"
}
trap cleanup EXIT

json_escape() {
    local value="${1-}"
    value=${value//\\/\\\\}
    value=${value//\"/\\\"}
    value=${value//$'\n'/\\n}
    value=${value//$'\r'/}
    value=${value//$'\t'/\\t}
    printf '%s' "$value"
}

spdx_id() {
    local value="$1"
    value="$(printf '%s' "$value" | tr -cs 'A-Za-z0-9.-' '-' | sed 's/^-//; s/-$//')"
    if [[ -z "$value" ]]; then
        value="unknown"
    fi
    printf 'SPDXRef-%s' "$value"
}

pacman_field() {
    local package_name="$1"
    local field_name="$2"

    pacman -Si "$package_name" 2>/dev/null | awk -F ':' -v wanted="$field_name" '
        $1 ~ "^[[:space:]]*" wanted "[[:space:]]*$" {
            sub(/^[[:space:]]+/, "", $2)
            print $2
            exit
        }
    '
}

append_described() {
    local id="$1"

    if [[ -s "$tmp_dir/describes.json" ]]; then
        printf ',\n' >> "$tmp_dir/describes.json"
    fi
    printf '    "%s"' "$(json_escape "$id")" >> "$tmp_dir/describes.json"
}

append_package() {
    local id="$1"
    local name="$2"
    local download_location="$3"
    local license_declared="$4"
    local version_info="$5"
    local summary="$6"
    local license_comments="$7"
    local source_info="$8"
    local homepage="$9"
    local external_ref="${10}"

    if [[ -s "$tmp_dir/packages.json" ]]; then
        printf ',\n' >> "$tmp_dir/packages.json"
    fi

    printf '    {\n' >> "$tmp_dir/packages.json"
    printf '      "SPDXID": "%s",\n' "$(json_escape "$id")" >> "$tmp_dir/packages.json"
    printf '      "name": "%s",\n' "$(json_escape "$name")" >> "$tmp_dir/packages.json"
    printf '      "downloadLocation": "%s",\n' "$(json_escape "${download_location:-NOASSERTION}")" >> "$tmp_dir/packages.json"
    printf '      "filesAnalyzed": false,\n' >> "$tmp_dir/packages.json"
    printf '      "licenseConcluded": "NOASSERTION",\n' >> "$tmp_dir/packages.json"
    printf '      "licenseDeclared": "%s",\n' "$(json_escape "${license_declared:-NOASSERTION}")" >> "$tmp_dir/packages.json"
    printf '      "copyrightText": "NOASSERTION"' >> "$tmp_dir/packages.json"

    if [[ -n "$version_info" ]]; then
        printf ',\n      "versionInfo": "%s"' "$(json_escape "$version_info")" >> "$tmp_dir/packages.json"
    fi
    if [[ -n "$summary" ]]; then
        printf ',\n      "summary": "%s"' "$(json_escape "$summary")" >> "$tmp_dir/packages.json"
    fi
    if [[ -n "$homepage" ]]; then
        printf ',\n      "homepage": "%s"' "$(json_escape "$homepage")" >> "$tmp_dir/packages.json"
    fi
    if [[ -n "$license_comments" ]]; then
        printf ',\n      "licenseComments": "%s"' "$(json_escape "$license_comments")" >> "$tmp_dir/packages.json"
    fi
    if [[ -n "$source_info" ]]; then
        printf ',\n      "sourceInfo": "%s"' "$(json_escape "$source_info")" >> "$tmp_dir/packages.json"
    fi
    if [[ -n "$external_ref" ]]; then
        printf ',\n      "externalRefs": [\n' >> "$tmp_dir/packages.json"
        printf '        {\n' >> "$tmp_dir/packages.json"
        printf '          "referenceCategory": "PACKAGE-MANAGER",\n' >> "$tmp_dir/packages.json"
        printf '          "referenceType": "purl",\n' >> "$tmp_dir/packages.json"
        printf '          "referenceLocator": "%s"\n' "$(json_escape "$external_ref")" >> "$tmp_dir/packages.json"
        printf '        }\n' >> "$tmp_dir/packages.json"
        printf '      ]' >> "$tmp_dir/packages.json"
    fi

    printf '\n    }' >> "$tmp_dir/packages.json"
    append_described "$id"
}

mkdir -p "$licenses_dir"

mapfile -t dll_paths < <(find "$package_dir" -maxdepth 1 -type f -name '*.dll' | sort)
if [[ ${#dll_paths[@]} -eq 0 ]]; then
    echo "No DLLs were found under $package_dir." >&2
    exit 1
fi

declare -A package_versions
declare -A package_licenses
declare -A package_urls
declare -A package_descriptions
declare -A package_license_dirs
declare -A package_dlls
declare -A dll_owners
declare -A dll_owner_versions

printf 'dll\tpackage\tpackage_version\tlicenses\tlicense_dir\n' > "$inventory_file"

for dll_path in "${dll_paths[@]}"; do
    dll_name="$(basename "$dll_path")"
    owner_line="$(pacman -Qo "$dll_path" 2>/dev/null || true)"
    package_name="unknown"
    package_version=""

    if [[ "$owner_line" =~ is[[:space:]]owned[[:space:]]by[[:space:]]([^[:space:]]+)[[:space:]]([^[:space:]]+) ]]; then
        package_name="${BASH_REMATCH[1]}"
        package_version="${BASH_REMATCH[2]}"
    fi

    dll_owners["$dll_name"]="$package_name"
    dll_owner_versions["$dll_name"]="$package_version"

    if [[ "$package_name" != "unknown" && -z "${package_versions[$package_name]+x}" ]]; then
        package_versions["$package_name"]="$package_version"
        package_licenses["$package_name"]="$(pacman_field "$package_name" 'Licenses')"
        package_urls["$package_name"]="$(pacman_field "$package_name" 'URL')"
        package_descriptions["$package_name"]="$(pacman_field "$package_name" 'Description')"

        license_source_dir="$MINGW_PREFIX/share/licenses/$package_name"
        if [[ -d "$license_source_dir" ]]; then
            mkdir -p "$licenses_dir/$package_name"
            cp -R "$license_source_dir"/. "$licenses_dir/$package_name"/
            package_license_dirs["$package_name"]="repo-notices/windows/licenses/$package_name"
        else
            package_license_dirs["$package_name"]=""
        fi
    fi

    if [[ "$package_name" != "unknown" ]]; then
        if [[ -n "${package_dlls[$package_name]:-}" ]]; then
            package_dlls["$package_name"]+=", "
        fi
        package_dlls["$package_name"]+="$dll_name"
    fi

    printf '%s\t%s\t%s\t%s\t%s\n' \
        "$dll_name" \
        "$package_name" \
        "$package_version" \
        "${package_licenses[$package_name]:-}" \
        "${package_license_dirs[$package_name]:-}" \
        >> "$inventory_file"
done

{
    echo "Bundled runtime DLL notice summary"
    echo
    echo "Generated from the packaged DLL set under $(basename "$package_dir")."
    echo
    while IFS= read -r package_name; do
        [[ -n "$package_name" ]] || continue
        echo "Package: $package_name"
        echo "Version: ${package_versions[$package_name]:-unknown}"
        echo "Licenses: ${package_licenses[$package_name]:-unknown}"
        echo "URL: ${package_urls[$package_name]:-unknown}"
        echo "DLLs: ${package_dlls[$package_name]:-none}"
        if [[ -n "${package_license_dirs[$package_name]:-}" ]]; then
            echo "License files: ${package_license_dirs[$package_name]}"
        else
            echo "License files: missing"
        fi
        echo
    done < <(printf '%s\n' "${!package_versions[@]}" | sort)
} > "$summary_file"

: > "$tmp_dir/describes.json"
: > "$tmp_dir/packages.json"

append_package \
    "$(spdx_id 'r47-android-source')" \
    "r47_android" \
    "${android_source_repository_url:-NOASSERTION}" \
    "GPL-3.0-only" \
    "${android_source_commit:-unknown}" \
    "Android fork source repository for the packaged Windows simulator artifact." \
    "" \
    "Recorded from the workflow checkout used for this artifact." \
    "" \
    ""

if [[ -n "$upstream_source_repository_url" && -n "$upstream_source_commit" ]]; then
    append_package \
        "$(spdx_id 'r47-upstream-core')" \
        "r47-upstream-core" \
        "$upstream_source_repository_url" \
        "GPL-3.0-only" \
        "$upstream_source_commit" \
        "Authoritative upstream core revision synchronized into the Windows simulator build." \
        "" \
        "Recorded from the workflow upstream resolution step." \
        "" \
        ""
fi

append_package \
    "$(spdx_id 'xlsxio')" \
    "xlsxio" \
    "${xlsxio_source_repository_url:-NOASSERTION}" \
    "MIT" \
    "${xlsxio_source_commit:-unknown}" \
    "Vendored spreadsheet I/O dependency shipped with the Windows simulator artifact." \
    "" \
    "Version and source URL come from the shared Android defaults or explicit workflow inputs." \
    "" \
    ""

while IFS= read -r package_name; do
    [[ -n "$package_name" ]] || continue
    append_package \
        "$(spdx_id "msys2-$package_name")" \
        "$package_name" \
        "${package_urls[$package_name]:-NOASSERTION}" \
        "NOASSERTION" \
        "${package_versions[$package_name]:-}" \
        "MSYS2 package providing bundled runtime DLLs: ${package_dlls[$package_name]:-none}." \
        "${package_licenses[$package_name]:-unknown}" \
        "License files copied to ${package_license_dirs[$package_name]:-missing}." \
        "${package_urls[$package_name]:-}" \
        "pkg:generic/$package_name@${package_versions[$package_name]:-unknown}"
done < <(printf '%s\n' "${!package_versions[@]}" | sort)

for dll_path in "${dll_paths[@]}"; do
    dll_name="$(basename "$dll_path")"
    append_package \
        "$(spdx_id "windows-dll-$dll_name")" \
        "$dll_name" \
        "NOASSERTION" \
        "NOASSERTION" \
        "${dll_owner_versions[$dll_name]:-}" \
        "Bundled runtime DLL copied into the packaged Windows simulator artifact." \
        "" \
        "Owned by ${dll_owners[$dll_name]:-unknown}." \
        "" \
        ""
done

{
    echo '{'
    echo '  "spdxVersion": "SPDX-2.3",'
    echo '  "dataLicense": "CC0-1.0",'
    echo '  "SPDXID": "SPDXRef-DOCUMENT",'
    echo '  "name": "R47 Windows simulator third-party inventory",'
    printf '  "documentNamespace": "https://r47.invalid/spdx/windows/%s",\n' "$(json_escape "${android_source_commit:-unknown}")"
    echo '  "creationInfo": {'
    printf '    "created": "%s",\n' "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
    echo '    "creators": ['
    echo '      "Tool: generate_windows_runtime_notice_artifacts.sh"'
    echo '    ]'
    echo '  },'
    echo '  "documentDescribes": ['
    cat "$tmp_dir/describes.json"
    echo
    echo '  ],'
    echo '  "packages": ['
    cat "$tmp_dir/packages.json"
    echo
    echo '  ]'
    echo '}'
} > "$spdx_file"