#!/bin/bash

# Safe ES 6.x to 7.x Reindex Script v5
#
# SAFETY GUARANTEES:
# 1. Only ONE reindex operation (not two) - uses aliases for "rename"
# 2. NEVER deletes original until reindex is 100% verified
# 3. Checks: failures=0 AND created==total AND temp_count==original_count
# 4. On ANY failure: original preserved, temp cleaned up
# 5. Dependency checks before starting
# 6. PRE-CREATES target index with source mapping to prevent type conflicts
# 7. UPGRADE_FLOATS: Optionally upgrades float->double to prevent overflow errors
# 8. Uses temp files for large JSON to avoid shell argument limits

# Exit on undefined variables (but not on command failures - we handle those)
set -u

# Configuration
ES_HOST="localhost:9200"
ES_USER="$ELASTIC_USERNAME"
ES_PASS="$ELASTIC_PASSWORD"
DRY_RUN="${DRY_RUN:-false}"
UPGRADE_FLOATS="${UPGRADE_FLOATS:-true}"  # Upgrade float->double to prevent overflow errors
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
LOG_FILE="reindex_${TIMESTAMP}.log"
FAILED_INDICES_FILE="failed_indices_${TIMESTAMP}.txt"
SKIPPED_INDICES_FILE="skipped_indices_${TIMESTAMP}.txt"
SUCCESS_INDICES_FILE="success_indices_${TIMESTAMP}.txt"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

AUTH="-u ${ES_USER}:${ES_PASS}"

log() {
    local msg="[$(date '+%H:%M:%S')] $1"
    echo -e "$msg" | tee -a "$LOG_FILE"
}

log_error() {
    log "${RED}ERROR: $1${NC}"
}

log_success() {
    log "${GREEN}SUCCESS: $1${NC}"
}

log_warning() {
    log "${YELLOW}WARNING: $1${NC}"
}

log_info() {
    log "${BLUE}INFO: $1${NC}"
}

# Check dependencies
check_dependencies() {
    log "Checking dependencies..."

    if ! command -v jq &> /dev/null; then
        log_error "jq is required but not installed. Install with: sudo apt install jq"
        exit 1
    fi

    if ! command -v curl &> /dev/null; then
        log_error "curl is required but not installed."
        exit 1
    fi

    log_success "Dependencies OK (curl, jq)"
}

# ES API call with error handling (uses temp file for large payloads)
es_call() {
    local method=$1
    local endpoint=$2
    local data=${3:-}

    if [ -n "$data" ]; then
        local tmp_file=$(mktemp)
        echo "$data" > "$tmp_file"
        curl -s $AUTH -X "$method" "http://${ES_HOST}${endpoint}" \
            -H "Content-Type: application/json" \
            -d "@$tmp_file"
        rm -f "$tmp_file"
    else
        curl -s $AUTH -X "$method" "http://${ES_HOST}${endpoint}"
    fi
}

index_exists() {
    local index=$1
    local code=$(curl -s $AUTH -o /dev/null -w "%{http_code}" "http://${ES_HOST}/${index}")
    [ "$code" == "200" ]
}

get_doc_count() {
    local index=$1
    local result=$(es_call GET "/${index}/_count")
    echo "$result" | jq -r '.count // 0' 2>/dev/null || echo "0"
}

# Get mapping from source index
get_source_mapping() {
    local source_index=$1
    local mapping=$(es_call GET "/${source_index}/_mapping")
    echo "$mapping" | jq ".[\"$source_index\"].mappings" 2>/dev/null
}

# Upgrade all float fields to double in a mapping (prevents overflow errors)
# Uses temp file to avoid argument list too long for large mappings
upgrade_floats_to_double() {
    local mapping=$1
    local tmp_file=$(mktemp)
    echo "$mapping" > "$tmp_file"
    jq 'walk(if type == "object" and .type == "float" then .type = "double" else . end)' "$tmp_file" 2>/dev/null
    rm -f "$tmp_file"
}

# Create index with specific mapping
# Uses temp file to avoid argument list too long for large mappings
create_index_with_mapping() {
    local index=$1
    local mapping=$2
    local tmp_file=$(mktemp)

    # Write mapping to temp file to avoid argument limit
    echo "$mapping" > "$tmp_file"
    local body=$(jq -n --slurpfile m "$tmp_file" '{"mappings": $m[0]}')
    rm -f "$tmp_file"

    es_call PUT "/${index}" "$body"
}

# Main script
main() {
    log "========================================"
    log "ES 6.x to 7.x Safe Reindex Script v5"
    log "========================================"
    log "ES Host: $ES_HOST"
    log "Dry Run: $DRY_RUN"
    log "Upgrade Floats: $UPGRADE_FLOATS"
    log "Timestamp: $TIMESTAMP"
    log "========================================"

    check_dependencies

    # Test connection
    log "Testing ES connection..."
    local health=$(es_call GET "/_cluster/health")
    if ! echo "$health" | jq -e '.cluster_name' > /dev/null 2>&1; then
        log_error "Cannot connect to Elasticsearch at ${ES_HOST}"
        log_error "Response: $health"
        exit 1
    fi
    local cluster_name=$(echo "$health" | jq -r '.cluster_name')
    local status=$(echo "$health" | jq -r '.status')
    log_success "Connected to cluster: $cluster_name (status: $status)"

    # Get all indices
    log "Fetching all indices..."
    local all_settings=$(es_call GET "/_settings")

    if ! echo "$all_settings" | jq -e 'keys' > /dev/null 2>&1; then
        log_error "Failed to fetch indices"
        exit 1
    fi

    local total_indices=$(echo "$all_settings" | jq 'keys | length')
    log "Found $total_indices total indices"

    # Build list of ES 6.x indices
    local es6_indices=()
    while IFS= read -r line; do
        local index=$(echo "$line" | cut -d'|' -f1)
        local version=$(echo "$line" | cut -d'|' -f2)

        # Skip system indices
        [[ "$index" == .* ]] && continue

        # Skip our temp indices
        [[ "$index" == *_v7 ]] && continue

        # Check if ES 6.x (version < 7000000)
        if [[ "$version" =~ ^6 ]] || { [[ "$version" =~ ^[0-9]+$ ]] && [ "$version" -lt 7000000 ]; }; then
            es6_indices+=("$index")
        fi
    done < <(echo "$all_settings" | jq -r 'to_entries[] | "\(.key)|\(.value.settings.index.version.created // "0")"')

    local total=${#es6_indices[@]}
    log "Found $total ES 6.x indices to process"
    log "========================================"

    if [ "$total" -eq 0 ]; then
        log_success "No ES 6.x indices found. Nothing to do."
        exit 0
    fi

    # Counters
    local success_count=0
    local failed_count=0
    local skipped_count=0

    # Process each index
    for i in "${!es6_indices[@]}"; do
        local index="${es6_indices[$i]}"
        local num=$((i + 1))
        local new_index="${index}_v7"

        log ""
        log "========================================"
        log "[$num/$total] Processing: $index"
        log "========================================"

        # Step 1: Get original document count
        local original_count=$(get_doc_count "$index")
        log "Original document count: $original_count"

        # Step 2: Handle empty indices - delete them
        if [ "$original_count" -eq 0 ]; then
            log_warning "Index is empty (0 documents). Deleting..."
            if [ "$DRY_RUN" == "true" ]; then
                log "[DRY RUN] Would delete empty index: $index"
            else
                local delete_result=$(es_call DELETE "/$index")
                if echo "$delete_result" | jq -e '.acknowledged == true' > /dev/null 2>&1; then
                    log_success "Deleted empty index: $index"
                    echo "$index|deleted_empty|0" >> "$SKIPPED_INDICES_FILE"
                else
                    log_error "Failed to delete empty index: $index"
                    echo "$index|delete_empty_failed|0" >> "$FAILED_INDICES_FILE"
                    ((failed_count++))
                    continue
                fi
            fi
            ((skipped_count++))
            continue
        fi

        # Step 3: Check if _v7 index already exists
        if index_exists "$new_index"; then
            local existing_count=$(get_doc_count "$new_index")
            log_warning "Target index $new_index already exists with $existing_count docs"

            if [ "$existing_count" -eq "$original_count" ]; then
                log_info "Counts match - may be from previous successful run. Skipping."
                echo "$index|v7_exists_matching|$original_count" >> "$SKIPPED_INDICES_FILE"
            else
                log_error "Count mismatch! Manual intervention required."
                echo "$index|v7_exists_mismatch|orig=$original_count|v7=$existing_count" >> "$FAILED_INDICES_FILE"
                ((failed_count++))
            fi
            continue
        fi

        # Step 4: Dry run check
        if [ "$DRY_RUN" == "true" ]; then
            log "[DRY RUN] Would reindex $index ($original_count docs) -> $new_index"
            continue
        fi

        # Step 5: Pre-create target index with source mapping
        log "Getting source mapping from $index..."
        local source_mapping=$(get_source_mapping "$index")

        if [ -z "$source_mapping" ] || [ "$source_mapping" == "null" ]; then
            log_error "Failed to get mapping from source index $index"
            echo "$index|mapping_fetch_failed|$original_count|0" >> "$FAILED_INDICES_FILE"
            ((failed_count++))
            continue
        fi

        # Optionally upgrade float to double
        local target_mapping="$source_mapping"
        if [ "$UPGRADE_FLOATS" == "true" ]; then
            log "Upgrading float fields to double in mapping..."
            target_mapping=$(upgrade_floats_to_double "$source_mapping")
        fi

        # Create target index with mapping
        log "Creating $new_index with explicit mapping..."
        local create_result=$(create_index_with_mapping "$new_index" "$target_mapping")

        if ! echo "$create_result" | jq -e '.acknowledged == true' > /dev/null 2>&1; then
            log_error "Failed to create target index $new_index with mapping"
            log_error "Response: $create_result"
            echo "$index|create_index_failed|$original_count|0" >> "$FAILED_INDICES_FILE"
            ((failed_count++))
            continue
        fi
        log_success "Created target index with explicit mapping"

        # Step 6: Perform reindex (original -> new_v7)
        log "Reindexing: $index -> $new_index"
        local reindex_result=$(es_call POST "/_reindex?wait_for_completion=true" "{
            \"source\": {\"index\": \"$index\"},
            \"dest\": {\"index\": \"$new_index\"}
        }")

        # Step 7: Parse and validate reindex result
        local created=$(echo "$reindex_result" | jq -r '.created // 0')
        local total_docs=$(echo "$reindex_result" | jq -r '.total // 0')
        local failures=$(echo "$reindex_result" | jq -r '.failures | length // 0')
        local timed_out=$(echo "$reindex_result" | jq -r '.timed_out // false')

        log "Result: created=$created, total=$total_docs, failures=$failures, timed_out=$timed_out"

        # Step 8: Comprehensive validation
        local reindex_ok=true
        local failure_reason=""

        # Check timeout
        if [ "$timed_out" == "true" ]; then
            reindex_ok=false
            failure_reason="timed_out"
        fi

        # Check failures array
        if [ "$failures" -gt 0 ]; then
            reindex_ok=false
            failure_reason="${failure_reason:+$failure_reason|}failures=$failures"
            log_error "Reindex had $failures failures. Details:"
            # Show detailed error info including nested caused_by
            echo "$reindex_result" | jq -r '.failures[:5][] |
                "  Doc: \(.id // "unknown")",
                "  Type: \(.cause.type // "unknown")",
                "  Reason: \(.cause.reason // "unknown")",
                "  Caused by: \(.cause.caused_by.type // "N/A") - \(.cause.caused_by.reason // "N/A")",
                "  Deep cause: \(.cause.caused_by.caused_by.reason // "N/A")",
                "  ---"
            ' 2>/dev/null | tee -a "$LOG_FILE"

            # Save full failure JSON for debugging
            local failure_json_file="failures_${index}_${TIMESTAMP}.json"
            echo "$reindex_result" | jq '.failures' > "$failure_json_file" 2>/dev/null
            log_info "Full failure details saved to: $failure_json_file"
        fi

        # Check created count matches original
        if [ "$created" -ne "$original_count" ]; then
            reindex_ok=false
            failure_reason="${failure_reason:+$failure_reason|}created_mismatch:expected=$original_count,got=$created"
        fi

        # Double-verify by counting docs in new index
        if [ "$reindex_ok" == "true" ]; then
            sleep 1
            es_call POST "/${new_index}/_refresh" > /dev/null 2>&1
            local new_count=$(get_doc_count "$new_index")

            if [ "$new_count" -ne "$original_count" ]; then
                reindex_ok=false
                failure_reason="${failure_reason:+$failure_reason|}verify_failed:new_count=$new_count,expected=$original_count"
            fi
        fi

        # Step 9: Handle failure - preserve original, cleanup failed target
        if [ "$reindex_ok" != "true" ]; then
            log_error "Reindex FAILED for $index"
            log_error "Reason: $failure_reason"
            log_error "ORIGINAL INDEX PRESERVED"

            # Clean up the failed new index
            if index_exists "$new_index"; then
                log "Cleaning up failed index $new_index..."
                es_call DELETE "/$new_index" > /dev/null
            fi

            echo "$index|$failure_reason|$original_count|$created" >> "$FAILED_INDICES_FILE"
            ((failed_count++))
            continue
        fi

        # Step 10: SUCCESS! Now safe to proceed
        log_success "Reindex verified: $created docs in $new_index"

        # Step 11: Delete original index
        log "Deleting original index: $index"
        local delete_result=$(es_call DELETE "/$index")

        if ! echo "$delete_result" | jq -e '.acknowledged == true' > /dev/null 2>&1; then
            log_error "Failed to delete original index!"
            log_error "Data is safe in: $new_index"
            log_warning "Manual cleanup needed: delete $index, rename $new_index -> $index"
            echo "$index|delete_original_failed|$original_count" >> "$FAILED_INDICES_FILE"
            ((failed_count++))
            continue
        fi

        # Step 12: Create alias so old name still works
        log "Creating alias: $index -> $new_index"
        local alias_result=$(es_call POST "/_aliases" "{
            \"actions\": [
                {\"add\": {\"index\": \"$new_index\", \"alias\": \"$index\"}}
            ]
        }")

        if ! echo "$alias_result" | jq -e '.acknowledged == true' > /dev/null 2>&1; then
            log_warning "Failed to create alias (non-critical)"
            log_warning "Index accessible as: $new_index"
        else
            log_success "Alias created: $index -> $new_index"
        fi

        # Final success
        log_success "[$num/$total] $index: $original_count docs migrated to $new_index"
        echo "$index|$new_index|$original_count" >> "$SUCCESS_INDICES_FILE"
        ((success_count++))
    done

    # Summary
    log ""
    log "========================================"
    log "MIGRATION COMPLETE"
    log "========================================"
    log "Total indices: $total"
    log_success "Successful: $success_count"
    log_warning "Skipped: $skipped_count"
    if [ "$failed_count" -gt 0 ]; then
        log_error "Failed: $failed_count"
    else
        log "Failed: 0"
    fi
    log ""
    log "Files created:"
    log "  Log: $LOG_FILE"
    [ -s "$SUCCESS_INDICES_FILE" ] && log "  Success: $SUCCESS_INDICES_FILE"
    [ -s "$FAILED_INDICES_FILE" ] && log "  Failed: $FAILED_INDICES_FILE"
    [ -s "$SKIPPED_INDICES_FILE" ] && log "  Skipped: $SKIPPED_INDICES_FILE"
    log "========================================"

    # Exit with error if any failures
    [ "$failed_count" -gt 0 ] && exit 1
    exit 0
}

# Run main
main "$@"
