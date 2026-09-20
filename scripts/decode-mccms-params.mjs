const pageUrl = process.argv[2];
if (!pageUrl) {
  throw new Error("Usage: node decode-mccms-params.mjs <chapter-url>");
}

const getText = async (url) => {
  const response = await fetch(url, {
    headers: { "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36" },
  });
  if (!response.ok) throw new Error(`${response.status} ${url}`);
  return response.text();
};

const page = await getText(pageUrl);
const encrypted = page.match(/\bparams\s*=\s*['"]([^'"]+)['"]/s)?.[1];
if (!encrypted) throw new Error("Encrypted params were not found");

const scriptPath =
  page.match(/<script[^>]+src=['"]([^'"]*pic[^'"]*\.js[^'"]*)['"]/i)?.[1] ??
  page.match(/<script[^>]+src=['"]([^'"]*cms(?:\.min)?\.js[^'"]*)['"]/i)?.[1];
if (!scriptPath) throw new Error("Picture/CMS script was not found");

const scriptUrl = new URL(scriptPath, pageUrl).href;
const cryptoPath = page.match(/<script[^>]+src=['"]([^'"]*crypto-js[^'"]*\.js[^'"]*)['"]/i)?.[1] ??
  "/packs/js/crypto-js.min.js";
const cryptoUrl = new URL(cryptoPath, pageUrl).href;
const [cryptoSource, pictureSource] = await Promise.all([getText(cryptoUrl), getText(scriptUrl)]);

const CryptoJS = new Function(`${cryptoSource}; return CryptoJS;`)();
if (process.env.DEBUG_MCCMS_KEYS === "1") {
  const originalParse = CryptoJS.enc.Utf8.parse;
  CryptoJS.enc.Utf8.parse = (value) => {
    process.stderr.write(`UTF8_KEY=${value}\n`);
    return originalParse(value);
  };
  const originalDecrypt = CryptoJS.AES.decrypt;
  CryptoJS.AES.decrypt = (ciphertext, key, config) => {
    process.stderr.write(`AES_IV=${config.iv.toString()} CIPHER=${ciphertext.ciphertext.toString()}\n`);
    return originalDecrypt(ciphertext, key, config);
  };
}
let decoded;
if (pictureSource.includes("params=decryptParams(params);")) {
  const marker = "params=decryptParams(params);";
  const end = pictureSource.indexOf(marker);
  const obfuscatedStart = pictureSource.lastIndexOf("var _0xod", end);
  const decryptor = pictureSource
    .slice(obfuscatedStart >= 0 ? obfuscatedStart : 0, end + marker.length)
    .replace(/^\s*\$\(function\s*\(\)\s*\{\s*/, "");
  const host = new URL(pageUrl).host;
  decoded = new Function("CryptoJS", "window", "document", "$", "params", `${decryptor}; return params;`)(
    CryptoJS,
    { location: { host }, innerHeight: 1080 },
    { documentElement: { clientHeight: 1080 } },
    () => ({}),
    encrypted,
  );
} else if (pictureSource.includes("decrypt:function")) {
  const window = { location: { hostname: new URL(pageUrl).hostname } };
  let parsed;
  const JsonProxy = {
    ...JSON,
    parse(value) {
      parsed = JSON.parse(value);
      return parsed;
    },
  };
  const CMS = new Function("CryptoJS", "window", "JSON", `${pictureSource}; return CMS;`)(CryptoJS, window, JsonProxy);
  decoded = CMS.chapter.decrypt(encrypted);
  if (Array.isArray(decoded) && decoded.length === 0 && parsed) decoded = parsed;
} else {
  throw new Error("A supported decryptor was not found");
}

process.stdout.write(`${JSON.stringify(decoded, null, 2)}\n`);
