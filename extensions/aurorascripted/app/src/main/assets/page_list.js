function pageList(input) {
  var data = (typeof input === "string") ? JSON.parse(input) : input;
  var html = data.html || "";
  var re = /data-aurora-image="([^"]+)"/g;
  var pages = [];
  var m;
  while ((m = re.exec(html)) !== null) {
    pages.push({ imageUrl: m[1], headers: { Referer: data.baseUrl + "/" } });
  }
  return JSON.stringify({
    pages: pages,
    allowedHosts: ["placehold.co"]
  });
}
