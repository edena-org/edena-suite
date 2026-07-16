/**
 * Generic JSON item editor used by the views/jsonedit partials: shows/edits a single entity as
 * JSON (fetched from the controller's get action), and saves new entities from pasted JSON, a JSON
 * array, or JSONL. Built on the vendored Ace editor (mode-json) with manual validation — Ace
 * workers are intentionally disabled (no worker file is vendored).
 *
 * All AJAX goes through the SAME controller actions as the regular UI (get/update/save), so
 * permissions apply unchanged; the CSRF header is added globally via $.ajaxSetup in main.scala.html.
 */

var jsonItemEditors = {};

function initJsonAceEditor(modalId, readOnly) {
    var editor = ace.edit(modalId + "Editor");
    editor.setTheme("ace/theme/github");
    editor.session.setMode("ace/mode/json");
    editor.session.setUseWorker(false); // no worker vendored; we validate ourselves below
    editor.setOptions({
        fontSize: "13px",
        showPrintMargin: false,
        tabSize: 2,
        useSoftTabs: true,
        showLineNumbers: true,
        wrap: true,
        readOnly: readOnly
    });
    editor.session.on('change', function () {
        validateAndAnnotateJsonEditor(modalId);
    });
    jsonItemEditors[modalId] = editor;
    return editor;
}

/**
 * Parses editor text as a single JSON value, a JSON array, or JSONL (one JSON per non-blank line).
 * Returns {items: [...]} on success or {errors: [{row, message}]} (0-based rows) on failure.
 */
function validateJsonText(text) {
    if (!text || !text.trim().length)
        return { items: [] };

    try {
        var parsed = JSON.parse(text);
        return { items: Array.isArray(parsed) ? parsed : [parsed] };
    } catch (e) {
        // not a single JSON document; try JSONL line-by-line
    }

    var items = [];
    var errors = [];
    var lines = text.split("\n");
    for (var i = 0; i < lines.length; i++) {
        var line = lines[i].trim();
        if (!line.length)
            continue;
        try {
            items.push(JSON.parse(line));
        } catch (e) {
            errors.push({ row: i, message: e.message });
        }
    }
    return errors.length ? { errors: errors } : { items: items };
}

function validateAndAnnotateJsonEditor(modalId) {
    var editor = jsonItemEditors[modalId];
    if (!editor)
        return { items: [] };

    var result = validateJsonText(editor.getValue());
    editor.session.setAnnotations((result.errors || []).map(function (e) {
        return { row: e.row, column: 0, text: e.message, type: "error" };
    }));
    return result;
}

var jsonItemEditorLoadedValues = {};

/**
 * Wires an item modal: on show, creates the editor (first time) and loads the item's JSON from
 * the controller's get action. The fetch is skipped when the editor holds unsaved edits — e.g.
 * when coming back from the update-confirmation dialog — so nothing the user typed is lost.
 */
function registerItemJsonModal(modalId, getUrl, editable) {
    $('#' + modalId).on('shown.bs.modal', function () {
        var editor = jsonItemEditors[modalId] || initJsonAceEditor(modalId, !editable);
        editor.resize(); // initialized while hidden => must resize once visible

        var lastLoaded = jsonItemEditorLoadedValues[modalId];
        if (lastLoaded !== undefined && editor.getValue() !== lastLoaded)
            return; // unsaved edits present - don't overwrite them

        $.ajax({
            url: getUrl,
            dataType: 'json'
        }).done(function (data) {
            var text = JSON.stringify(data, null, 2);
            editor.setValue(text, -1);
            jsonItemEditorLoadedValues[modalId] = text;
            editor.session.setAnnotations([]);
        }).fail(function (data) {
            $('#' + modalId).modal('hide');
            showErrorResponse(data);
        });
    });
}

/** Wires a save-new modal: on first show, creates an empty editable editor. */
function registerSaveJsonModal(modalId) {
    $('#' + modalId).on('shown.bs.modal', function () {
        var editor = jsonItemEditors[modalId] || initJsonAceEditor(modalId, false);
        editor.resize();
        editor.focus();
    });
}

function copyJsonEditor(modalId) {
    var editor = jsonItemEditors[modalId];
    if (!editor)
        return;
    var text = editor.getValue();

    function done() {
        showMessage("JSON copied to clipboard.", false);
    }

    if (navigator.clipboard && window.isSecureContext) {
        navigator.clipboard.writeText(text).then(done);
    } else {
        // http / older browsers fallback
        var textArea = document.createElement("textarea");
        textArea.value = text;
        textArea.style.position = "fixed";
        textArea.style.opacity = "0";
        document.body.appendChild(textArea);
        textArea.select();
        document.execCommand('copy');
        document.body.removeChild(textArea);
        done();
    }
}

/** POSTs the (single-object) editor content as JSON to the controller's update action. */
function updateItemJsonFromEditor(modalId, updateUrl) {
    var result = validateAndAnnotateJsonEditor(modalId);
    if (result.errors) {
        showError("The JSON is not valid — fix the highlighted line(s) first.");
        return;
    }
    if (result.items.length !== 1 || Array.isArray(result.items[0]) || typeof result.items[0] !== 'object') {
        showError("Update expects exactly one JSON object.");
        return;
    }

    $.ajax({
        type: 'POST',
        url: updateUrl,
        data: JSON.stringify(result.items[0]),
        contentType: 'application/json',
        dataType: 'json'
    }).done(function (data) {
        $('#' + modalId).modal('hide');
        showMessage(data.message);
        // When the update produces a new location (e.g. a versioned entity whose update
        // creates a NEW id), the controller returns redirectUrl and we navigate there;
        // otherwise reload the current page in place (unchanged behaviour).
        setTimeout(function () {
            if (data && data.redirectUrl) { window.location.href = data.redirectUrl; }
            else { location.reload(); }
        }, 700);
    }).fail(handleServerJsonErrors(modalId));
}

/**
 * POSTs the editor content (object, array, or JSONL — normalized to a JSON array client-side) to
 * the controller's save action, with the selected key-handling mode (keep/regenerate/replace)
 * passed as the keyMode query param.
 */
function saveItemsJsonFromEditor(modalId, saveUrl) {
    var result = validateAndAnnotateJsonEditor(modalId);
    if (result.errors) {
        showError("The JSON is not valid — fix the highlighted line(s) first.");
        return;
    }
    if (!result.items.length) {
        showError("Nothing to save — provide a JSON object, a JSON array, or JSONL lines.");
        return;
    }

    var keyMode = $('#' + modalId + ' input[name="keyMode"]:checked').val() || 'keep';
    var url = saveUrl + (saveUrl.indexOf('?') >= 0 ? '&' : '?') + 'keyMode=' + keyMode;
    var payload = result.items.length === 1 ? result.items[0] : result.items;

    $.ajax({
        type: 'POST',
        url: url,
        data: JSON.stringify(payload),
        contentType: 'application/json',
        dataType: 'json'
    }).done(function (data) {
        $('#' + modalId).modal('hide');
        showMessage(data.message);
        setTimeout(function () { location.reload(); }, 700);
    }).fail(handleServerJsonErrors(modalId));
}

/**
 * Failure handler surfacing the server's per-item validation errors (see JsonBodyUtil.errorsToJson):
 * line-carrying errors become editor annotations, everything lands in the error banner.
 */
function handleServerJsonErrors(modalId) {
    return function (xhr) {
        var response = xhr.responseJSON;
        if (response && response.errors) {
            var editor = jsonItemEditors[modalId];
            if (editor) {
                editor.session.setAnnotations(response.errors.filter(function (e) {
                    return e.line;
                }).map(function (e) {
                    return { row: e.line - 1, column: 0, text: JSON.stringify(e.errors), type: "error" };
                }));
            }
            var details = response.errors.map(function (e) {
                var where = e.line ? ("line " + e.line) : ("item " + (e.index + 1));
                return where + ": " + JSON.stringify(e.errors);
            }).join("<br>");
            showError(response.message + "<br>" + details);
        } else if (response && response.message) {
            // e.g. a store error such as a duplicate key in keyMode=keep
            showError(response.message);
        } else {
            showErrorResponse(xhr);
        }
    };
}
