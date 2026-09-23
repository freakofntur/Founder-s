'use strict';

const fs = require('node:fs');
const path = require('node:path');

/**
 * A JSON file with debounced, atomic (write-then-rename) saves.
 * Good enough for a skeleton; swap for SQLite once data outgrows a single file.
 */
class JsonStore {
  /**
   * @param {string} filePath
   * @param {{ defaults?: object, debounceMs?: number, serialize?: () => any }} [opts]
   *   serialize: produce the data to write (defaults to `this.data`)
   */
  constructor(filePath, { defaults = {}, debounceMs = 1000, serialize } = {}) {
    this.filePath = filePath;
    this.debounceMs = debounceMs;
    this.serialize = serialize || (() => this.data);
    this.timer = null;
    this.data = { ...structuredClone(defaults), ...JsonStore.read(filePath) };
  }

  static read(filePath) {
    try {
      return JSON.parse(fs.readFileSync(filePath, 'utf8'));
    } catch (err) {
      if (err.code !== 'ENOENT') console.warn(`[store] could not read ${filePath}, starting fresh:`, err.message);
      return {};
    }
  }

  save() {
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.flush(), this.debounceMs);
  }

  flush() {
    if (this.timer === null) return;
    clearTimeout(this.timer);
    this.timer = null;
    fs.mkdirSync(path.dirname(this.filePath), { recursive: true });
    const tmp = `${this.filePath}.tmp`;
    fs.writeFileSync(tmp, JSON.stringify(this.serialize()));
    fs.renameSync(tmp, this.filePath);
  }
}

module.exports = { JsonStore };
