// Tool to verify/generate SafeGuard test cases using the upstream SafeGuard JS logic
import fs from 'node:fs';
import vm from 'node:vm';

const context = {
  globalThis: {},
  console: console,
  URL: URL,
  Set: Set,
  Map: Map,
  RegExp: RegExp,
  Array: Array,
  Object: Object,
  Date: Date,
};
context.globalThis = context;
context.SafeGuard = {};
context.SafeGuard.settings = {
  normalizeDomain: (h) => (h || '').toLowerCase().replace(/^www\./, ''),
  initialize: async () => {},
  shouldUpdate: async () => false,
};
context.SafeGuard.curatedResources = {
  base64StarredLinks: [],
  base64DecodedLinks: []
};

vm.runInNewContext(fs.readFileSync('.cache/ref/safeguard/config.js', 'utf8'), context);
vm.runInNewContext(fs.readFileSync('.cache/ref/safeguard/resources.js', 'utf8'), context);
vm.runInNewContext(fs.readFileSync('.cache/ref/safeguard/site-metadata.js', 'utf8'), context);
vm.runInNewContext(fs.readFileSync('.cache/ref/safeguard/catalogue.js', 'utf8'), context);

const unsafeText = fs.readFileSync('.cache/sitelist.txt', 'utf8');
const potentiallyUnsafeText = fs.readFileSync('.cache/sitelist-plus.txt', 'utf8');
const fmhyText = fs.readFileSync('.cache/fmhy-sites.txt', 'utf8');
const reasonsText = fs.readFileSync('.cache/filterlists-reasons.json', 'utf8');

const resources = context.SafeGuard.resources;
const unsafeSites = resources.extractUrlsFromFilterList(unsafeText);
const potentiallyUnsafeSites = resources.extractUrlsFromFilterList(potentiallyUnsafeText);
const fmhySites = resources.extractUrlsFromFilterList(fmhyText);
const unsafeReasons = JSON.parse(reasonsText);

const storageData = {
  unsafeSites,
  potentiallyUnsafeSites,
  fmhySites,
  unsafeReasons,
  safeSiteList: [],
  starredSites: [],
  fmhyResourceMap: {},
  resourceIdentityVersion: context.SafeGuard.config.resourceIdentityVersion,
};

const storage = {
  get: async () => storageData,
  set: async () => {},
};

const catalogue = context.SafeGuard.createCatalogue({ storage, fetch: async () => {} });
await catalogue.initialize();

export function classifyUrl(url) {
  if (resources.isSearchEngine(url)) {
    return { status: 'SEARCH_ENGINE', reason: null };
  }
  const res = catalogue.getSiteStatus(url);
  return {
    status: res.status,
    reason: res.reason
  };
}

console.log('SafeGuard fixture generator ready.');
