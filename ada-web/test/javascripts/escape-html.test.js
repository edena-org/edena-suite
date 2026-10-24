const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const { test } = require("node:test");

const source = fs.readFileSync(path.join(__dirname, "../../public/javascripts/main.js"), "utf8");
const start = source.indexOf("function escapeHtml(");
const end = source.indexOf("\nfunction prependTrollboxJsonMessage(", start);
const context = {};
vm.createContext(context);
vm.runInContext(source.slice(start, end), context);
const escapeHtml = context.escapeHtml;

test("markup in a message is escaped, to be shown as text", () => {
  assert.equal(
    escapeHtml('<b class="note">it\'s</b> & more'),
    "&lt;b class=&quot;note&quot;&gt;it&#39;s&lt;/b&gt; &amp; more"
  );
});

test("plain text, links included, stays as it is", () => {
  assert.equal(escapeHtml("see https://example.com/page?id=1"), "see https://example.com/page?id=1");
});
