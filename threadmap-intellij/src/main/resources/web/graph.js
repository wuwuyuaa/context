(function () {
  var STATUS_COLOR = { UNKNOWN: '#9AA0A6', HALF: '#D59B22', MASTERED: '#4CAF50', RISKY: '#D64545' };

  function escapeHtml(s) {
    return (s == null ? '' : String(s)).replace(/[&<>"]/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c];
    });
  }

  function render(graph) {
    var elements = [];
    graph.nodes.forEach(function (n) {
      elements.push({ data: {
        id: n.id, label: n.label, summary: n.summary, status: n.status,
        sideEffects: n.sideEffects || [], digWorthy: !!n.digWorthy
      } });
    });
    graph.edges.forEach(function (e) {
      elements.push({ data: {
        id: 'e_' + e.from + '__' + e.to, source: e.from, target: e.to,
        label: e.count > 1 ? ('×' + e.count) : ''
      } });
    });

    if (window.cytoscapeDagre) { cytoscape.use(window.cytoscapeDagre); }

    var cy = cytoscape({
      container: document.getElementById('cy'),
      elements: elements,
      style: [
        { selector: 'node', style: {
          'shape': 'round-rectangle', 'background-color': '#2b2d30',
          'width': '230px', 'height': '54px', 'border-width': 0 } },
        { selector: 'node:selected', style: { 'border-width': 2, 'border-color': '#3574f0' } },
        { selector: 'edge', style: {
          'curve-style': 'bezier', 'target-arrow-shape': 'triangle', 'width': 1.5,
          'line-color': '#6b7280', 'target-arrow-color': '#6b7280',
          'label': 'data(label)', 'font-size': '10px', 'color': '#c9ccd1',
          'text-background-color': '#1e1f22', 'text-background-opacity': 1,
          'text-background-padding': '2px' } }
      ],
      layout: { name: 'dagre', rankDir: 'TB', nodeSep: 28, rankSep: 52 },
      wheelSensitivity: 0.2
    });

    cy.nodeHtmlLabel([{
      query: 'node', halign: 'center', valign: 'center', halignBox: 'center', valignBox: 'center',
      tpl: function (d) {
        var color = STATUS_COLOR[d.status] || STATUS_COLOR.UNKNOWN;
        var pills = (d.sideEffects || []).map(function (s) {
          return '<span class="pill">' + escapeHtml(s) + '</span>';
        }).join('');
        var star = d.digWorthy ? ' <span class="star">★</span>' : '';
        return '<div class="card" style="border-left:4px solid ' + color + '">'
          + '<div class="title">' + escapeHtml(d.label) + star + '</div>'
          + '<div class="summary">' + escapeHtml(d.summary) + '</div>'
          + (pills ? '<div class="pills">' + pills + '</div>' : '')
          + '</div>';
      }
    }]);

    cy.on('tap', 'node', function (evt) {
      if (window.threadmapSelect) window.threadmapSelect(evt.target.id());
    });
    cy.on('dbltap', 'node', function (evt) {
      if (window.threadmapOpen) window.threadmapOpen(evt.target.id());
    });

    setupZoom(cy);
  }

  // JCEF 不把滚轮/触控板捏合喂给 Cytoscape,所以提供可点的缩放控件作为可靠入口。
  function setupZoom(cy) {
    function zoomBy(factor) {
      var c = cy.container();
      var center = { x: c.clientWidth / 2, y: c.clientHeight / 2 };
      cy.zoom({ level: cy.zoom() * factor, renderedPosition: center });
    }
    var zin = document.getElementById('zoom-in');
    var zout = document.getElementById('zoom-out');
    var zfit = document.getElementById('zoom-fit');
    if (zin) zin.onclick = function () { zoomBy(1.3); };
    if (zout) zout.onclick = function () { zoomBy(1 / 1.3); };
    if (zfit) zfit.onclick = function () { cy.fit(cy.elements(), 40); };
  }

  if (window.__GRAPH__) render(window.__GRAPH__);
})();
