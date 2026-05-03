#!/bin/bash

set -Eeuo pipefail

usage() {
    cat <<'EOF'
Usage:
  collect_packaging_evidence.sh \
    --variant <debug|release> \
    (--apk <path> | --bundle <path>) \
    --output-dir <path> \
    [--artifact-name <file-name>] \
    [--expected-abis <abi[,abi...]>] \
    [--android-sdk-root <path>] \
    [--ndk-version <version>] \
    [--compile-sdk <sdk>] \
    [--cmake-version <version>] \
    [--android-source-repository-url <url>] \
    [--android-source-commit <commit>] \
    [--upstream-source-repository-url <url>] \
    [--upstream-source-commit <commit>] \
    [--xlsxio-source-repository-url <url>] \
    [--xlsxio-source-commit <commit>] \
    [--signing-mode <debug|release|unsigned>] \
    [--ref <git-ref>] \
    [--sha <git-sha>] \
    [--run-id <id>] \
    [--run-attempt <attempt>] \
    [--mapping-file <path>] \
    [--native-symbols <path>]
EOF
}

variant=""
apk_path=""
bundle_path=""
output_dir=""
artifact_name=""
expected_abis=""
android_sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
ndk_version=""
compile_sdk=""
cmake_version=""
android_source_repository_url=""
android_source_commit=""
upstream_source_repository_url=""
upstream_source_commit=""
xlsxio_source_repository_url=""
xlsxio_source_commit=""
signing_mode="unknown"
git_ref=""
git_sha=""
run_id=""
run_attempt=""
mapping_file=""
native_symbols_file=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --variant)
            variant="$2"
            shift 2
            ;;
        --apk)
            apk_path="$2"
            shift 2
            ;;
        --bundle)
            bundle_path="$2"
            shift 2
            ;;
        --output-dir)
            output_dir="$2"
            shift 2
            ;;
        --artifact-name)
            artifact_name="$2"
            shift 2
            ;;
        --expected-abis)
            expected_abis="$2"
            shift 2
            ;;
        --android-sdk-root)
            android_sdk_root="$2"
            shift 2
            ;;
        --ndk-version)
            ndk_version="$2"
            shift 2
            ;;
        --compile-sdk)
            compile_sdk="$2"
            shift 2
            ;;
        --cmake-version)
            cmake_version="$2"
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
        --signing-mode)
            signing_mode="$2"
            shift 2
            ;;
        --ref)
            git_ref="$2"
            shift 2
            ;;
        --sha)
            git_sha="$2"
            shift 2
            ;;
        --run-id)
            run_id="$2"
            shift 2
            ;;
        --run-attempt)
            run_attempt="$2"
            shift 2
            ;;
        --mapping-file)
            mapping_file="$2"
            shift 2
            ;;
        --native-symbols)
            native_symbols_file="$2"
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

if [[ -z "$variant" || -z "$output_dir" ]]; then
    echo "Missing required arguments." >&2
    usage >&2
    exit 1
fi

if [[ -n "$apk_path" && -n "$bundle_path" ]]; then
    echo "Provide only one primary artifact: --apk or --bundle." >&2
    exit 1
fi

if [[ -z "$apk_path" && -z "$bundle_path" ]]; then
    echo "Provide one primary artifact with --apk or --bundle." >&2
    exit 1
fi

if [[ -n "$apk_path" && ! -f "$apk_path" ]]; then
    echo "APK not found: $apk_path" >&2
    exit 1
fi

if [[ -n "$bundle_path" && ! -f "$bundle_path" ]]; then
    echo "App bundle not found: $bundle_path" >&2
    exit 1
fi

if [[ -n "$mapping_file" && ! -f "$mapping_file" ]]; then
    echo "Mapping file not found: $mapping_file" >&2
    exit 1
fi

if [[ -n "$native_symbols_file" && ! -f "$native_symbols_file" ]]; then
    echo "Native symbols archive not found: $native_symbols_file" >&2
    exit 1
fi

mkdir -p "$output_dir"

primary_artifact_path="$apk_path"
artifact_type="apk"
if [[ -n "$bundle_path" ]]; then
    primary_artifact_path="$bundle_path"
    artifact_type="aab"
fi

if [[ -z "$artifact_name" ]]; then
    artifact_name="$(basename "$primary_artifact_path")"
fi

release_artifact="$output_dir/$artifact_name"
cp "$primary_artifact_path" "$release_artifact"

tmp_root="${TMPDIR:-$(dirname "$output_dir")}"
mkdir -p "$tmp_root"
tmp_dir="$(mktemp -d "$tmp_root/r47-packaging.XXXXXX")"
cleanup() {
    rm -rf "$tmp_dir"
}
trap cleanup EXIT

write_metadata_line() {
    local key="$1"
    local value="$2"
    local target_file="$3"
    if [[ -n "$value" ]]; then
        printf '%s=%s\n' "$key" "$value" >> "$target_file"
    fi
}

write_xlsxio_license() {
    local target_file="$1"
    cat > "$target_file" <<'EOF'
Copyright (C) 2016 Brecht Sanders All Rights Reserved

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
EOF
}

if [[ "$artifact_type" == "apk" ]]; then
    if [[ -z "$android_sdk_root" || -z "$ndk_version" ]]; then
        echo "APK evidence collection requires --android-sdk-root and --ndk-version." >&2
        exit 1
    fi

    unpack_dir="$tmp_dir/apk-unpacked"
    mkdir -p "$unpack_dir"
    unzip -q "$primary_artifact_path" -d "$unpack_dir"

    if [[ -n "$expected_abis" ]]; then
        expected_file="$tmp_dir/expected-abis.txt"
        actual_file="$output_dir/abis.txt"
        tr ',' '\n' <<< "$expected_abis" | sed '/^$/d' | sort > "$expected_file"
        find "$unpack_dir/lib" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort | tee "$actual_file"
        diff -u "$expected_file" "$actual_file"
    fi

    zipalign_bin="$(find "$android_sdk_root/build-tools" -type f -name zipalign | sort -V | tail -n 1)"
    llvm_objdump="$(find "$android_sdk_root/ndk/$ndk_version/toolchains/llvm/prebuilt" -type f -name llvm-objdump | sort | head -n 1)"

    if [[ -z "$zipalign_bin" || ! -x "$zipalign_bin" ]]; then
        echo "Unable to locate zipalign under $android_sdk_root/build-tools." >&2
        exit 1
    fi

    if [[ -z "$llvm_objdump" || ! -x "$llvm_objdump" ]]; then
        echo "Unable to locate llvm-objdump for NDK $ndk_version." >&2
        exit 1
    fi

    "$zipalign_bin" -c -P 16 -v 4 "$primary_artifact_path" | tee "$output_dir/zipalign.txt"

    elf_report="$output_dir/elf-load-segments.txt"
    below_16k_report="$tmp_dir/elf-load-segments-below-16k.txt"
    : > "$elf_report"
    while IFS= read -r -d '' so_file; do
        {
            echo "== ${so_file#$unpack_dir/} =="
            "$llvm_objdump" -p "$so_file" | grep 'LOAD'
            echo
        } >> "$elf_report"
    done < <(find "$unpack_dir/lib" -type f -name '*.so' -print0)

    awk '
        /^== / {
            current = $0
            next
        }

        /align 2\*\*[0-9]+/ {
            if (match($0, /align 2\*\*[0-9]+/)) {
                exponent_text = $0
                sub(/.*align 2\*\*/, "", exponent_text)
                sub(/[^0-9].*$/, "", exponent_text)
                exponent = exponent_text + 0
                if (exponent < 14) {
                    if (current != "" && current != last_header) {
                        print current
                        last_header = current
                    }
                    print $0
                }
            }
        }
    ' "$elf_report" > "$below_16k_report"

    if [[ -s "$below_16k_report" ]]; then
        cat "$below_16k_report" >&2
        echo "Detected a native library LOAD segment aligned below 16 KB." >&2
        exit 1
    fi
fi

if [[ -n "$mapping_file" ]]; then
    cp "$mapping_file" "$output_dir/$(basename "$mapping_file")"
fi

if [[ -n "$native_symbols_file" ]]; then
    cp "$native_symbols_file" "$output_dir/$(basename "$native_symbols_file")"
fi

if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$release_artifact" > "$output_dir/SHA256SUMS.txt"
elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$release_artifact" > "$output_dir/SHA256SUMS.txt"
else
    echo "Neither sha256sum nor shasum is available." >&2
    exit 1
fi

build_metadata_file="$output_dir/BUILD-METADATA.txt"
: > "$build_metadata_file"
write_metadata_line "ref" "$git_ref" "$build_metadata_file"
write_metadata_line "sha" "$git_sha" "$build_metadata_file"
write_metadata_line "run_id" "$run_id" "$build_metadata_file"
write_metadata_line "run_attempt" "$run_attempt" "$build_metadata_file"
write_metadata_line "upstream_url" "$upstream_source_repository_url" "$build_metadata_file"
write_metadata_line "upstream_commit" "$upstream_source_commit" "$build_metadata_file"
write_metadata_line "upstream_license_file" "COPYING" "$build_metadata_file"
write_metadata_line "android_source_repository_url" "$android_source_repository_url" "$build_metadata_file"
write_metadata_line "android_source_commit" "$android_source_commit" "$build_metadata_file"
write_metadata_line "source_manifest_file" "SOURCE" "$build_metadata_file"
write_metadata_line "xlsxio_url" "$xlsxio_source_repository_url" "$build_metadata_file"
write_metadata_line "xlsxio_commit" "$xlsxio_source_commit" "$build_metadata_file"
write_metadata_line "xlsxio_license_file" "LICENSE.txt" "$build_metadata_file"
write_metadata_line "compile_sdk" "$compile_sdk" "$build_metadata_file"
write_metadata_line "cmake_version" "$cmake_version" "$build_metadata_file"
write_metadata_line "ndk_version" "$ndk_version" "$build_metadata_file"
write_metadata_line "android_variant" "$variant" "$build_metadata_file"
write_metadata_line "artifact_type" "$artifact_type" "$build_metadata_file"
write_metadata_line "artifact_name" "$artifact_name" "$build_metadata_file"
write_metadata_line "artifact_signed" "$signing_mode" "$build_metadata_file"

source_manifest_file="$output_dir/SOURCE"
: > "$source_manifest_file"
write_metadata_line "upstream_url" "$upstream_source_repository_url" "$source_manifest_file"
write_metadata_line "upstream_commit" "$upstream_source_commit" "$source_manifest_file"
write_metadata_line "android_source_repository_url" "$android_source_repository_url" "$source_manifest_file"
write_metadata_line "android_source_commit" "$android_source_commit" "$source_manifest_file"
write_metadata_line "xlsxio_url" "$xlsxio_source_repository_url" "$source_manifest_file"
write_metadata_line "xlsxio_commit" "$xlsxio_source_commit" "$source_manifest_file"

cp "$(dirname "$0")/../COPYING" "$output_dir/COPYING"
write_xlsxio_license "$output_dir/LICENSE.txt"

echo "Packaging evidence written to $output_dir"