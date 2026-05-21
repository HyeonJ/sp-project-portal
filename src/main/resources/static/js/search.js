(function () {
    'use strict';
    if (!$('#searchPage').length) { return; }

    function esc(s) { return $('<div>').text(s == null ? '' : s).html(); }
    function qparam(k) { return new URLSearchParams(location.search).get(k); }

    const q = (qparam('q') || '').trim();
    $('#searchQuery').text(q ? `"${q}" 검색 결과` : '검색어를 입력하세요.');
    if (!q) { return; }

    function section(title, rows) {
        if (!rows.length) { return ''; }
        return `<section class="card" style="margin-bottom:14px"><div class="card-head"><h3>${esc(title)}</h3><span class="meta">${rows.length}</span></div><div class="card-list">${rows.join('')}</div></section>`;
    }

    $.getJSON('/api/search', { q: q }).done(function (res) {
        const d = res.data || {};
        const projects = (d.projects || []).map(p =>
            `<a class="item" href="/projects/${p.id}" style="text-decoration:none;color:var(--fg)"><span class="body"><b>${esc(p.name)}</b> <span class="cat-tag">${esc(p.clientOrgName || '')}</span></span></a>`);
        const tcs = (d.testCases || []).map(t =>
            `<a class="item" href="/projects/${t.projectId}/test-cases" style="text-decoration:none;color:var(--fg)"><span class="body"><b style="font-family:var(--mono)">${esc(t.code)}</b> ${esc(t.title)}</span></a>`);
        const defects = (d.defects || []).map(x =>
            `<a class="item" href="/projects/${x.projectId}/defects" style="text-decoration:none;color:var(--fg)"><span class="body"><b style="font-family:var(--mono)">${esc(x.code)}</b> ${esc(x.title)}</span></a>`);

        const html = section('프로젝트', projects) + section('테스트 케이스', tcs) + section('결함', defects);
        $('#searchResults').html(html || '<p class="page-sub">검색 결과가 없습니다.</p>');
    }).fail(function () {
        $('#searchResults').html('<p class="err" style="color:var(--hot)">검색 실패</p>');
    });
})();
