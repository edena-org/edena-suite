# Ace Editor Library Files

This directory contains locally hosted Ace Editor files for the Script Runner and JSON item editor features.

## Ace Editor v1.32.6
Downloaded from: https://cdnjs.cloudflare.com/ajax/libs/ace/1.32.6/
(`mode-json` from https://raw.githubusercontent.com/ajaxorg/ace-builds/v1.32.6/src-min-noconflict/mode-json.js)

### Files in this directory:
- `ace-1.32.6.js` - Main Ace Editor library (440KB)
- `ext-language_tools-1.32.6.js` - Language tools extension for autocompletion (55KB)
- `mode-python-1.32.6.js` - Python syntax highlighting mode (8KB)
- `mode-javascript-1.32.6.js` - JavaScript syntax highlighting mode (21KB)
- `mode-json-1.32.6.js` - JSON syntax highlighting mode (6KB); its worker is intentionally NOT
  vendored — the JSON item editor disables Ace workers and validates via `JSON.parse` itself
- `theme-monokai-1.32.6.js` - Monokai dark theme (3KB)
- `theme-github-1.32.6.js` - GitHub light theme (3KB)

**Total Size**: ~537KB

**Usage**: Used in the Script Runner (`/runScriptHome`) for Python and JavaScript code editing with syntax highlighting, and by the generic JSON item editor (`javascripts/jsonItemEditor.js`, `views/jsonedit/*`) for viewing/editing entities as JSON.

**Referenced from**: `/ada/app/views/runScript.scala.html`
- `/assets/javascripts/ace/ace-1.32.6.js`
- `/assets/javascripts/ace/mode-python-1.32.6.js`
- `/assets/javascripts/ace/mode-javascript-1.32.6.js`
- `/assets/javascripts/ace/theme-monokai-1.32.6.js`

**License**: Ace Editor is released under the BSD License.

**Last Updated**: September 17, 2025