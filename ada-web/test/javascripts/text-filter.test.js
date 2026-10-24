const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const { test } = require("node:test");

const source = fs.readFileSync(path.join(__dirname, "../../public/javascripts/main.js"), "utf8");
const start = source.indexOf("function textFilterCondition(");
const end = source.indexOf("\nfunction updateFilterValueElement(", start);
const context = {};
vm.createContext(context);
vm.runInContext(source.slice(start, end), context);
const condition = context.textFilterCondition;

test("new text filters default to full-text search", () => {
  assert.equal(condition("=", "", false), "contains");
});

test("editing an existing comparison does not silently turn it into contains", () => {
  assert.equal(condition("=", "short exact value", true), "=");
  assert.equal(condition("!=", "short exact value", true), "!=");
});

test("existing null comparisons retain their meaning with explicit presence operators", () => {
  assert.equal(condition("=", "", true), "is undefined");
  assert.equal(condition("!=", "  ", true), "is defined");
  assert.equal(condition("=", null, true), "is undefined");
});

test("existing supported operators are preserved", () => {
  for (const operator of ["contains", "is defined", "is undefined"]) {
    assert.equal(condition(operator, "", true), operator);
  }
});
