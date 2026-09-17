/**
 * LAN demo script: parses Host-fetched JSON in input.body. No network APIs.
 */
function requireBody(input) {
  if (!input || input.body == null || input.body === "") {
    throw new Error("missing body");
  }
  return JSON.parse(String(input.body));
}

function parsePopular(input) {
  var data = requireBody(input);
  return {
    mangas: data.mangas || [],
    hasNextPage: !!data.hasNextPage
  };
}

function parseLatest(input) {
  return parsePopular(input);
}

function parseSearch(input) {
  var data = requireBody(input);
  var q = (input && input.query) ? String(input.query) : "";
  var src = data.mangas || [];
  var mangas = [];
  for (var i = 0; i < src.length; i++) {
    var m = src[i];
    mangas.push({
      url: m.url,
      title: m.title + (q ? (" [" + q + "]") : ""),
      thumbnailUrl: m.thumbnailUrl
    });
  }
  return { mangas: mangas, hasNextPage: !!data.hasNextPage };
}

function parseDetails(input) {
  var data = requireBody(input);
  return {
    url: data.url || (input && input.mangaUrl) || "",
    title: data.title || "Untitled",
    author: data.author || "",
    description: data.description || "",
    thumbnailUrl: data.thumbnailUrl || "",
    initialized: true
  };
}

function parseChapters(input) {
  var data = requireBody(input);
  return { chapters: data.chapters || [] };
}

function parsePages(input) {
  var data = requireBody(input);
  return { pages: data.pages || [] };
}
