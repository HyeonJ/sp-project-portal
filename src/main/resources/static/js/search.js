(function () {
    'use strict';
    if (!$('#searchPage').length) { return; }

    function esc(s) { return $('<div>').text(s == null ? '' : s).html(); }
    function qparam(k) { return new URLSearchParams(location.search).get(k); }

    const q = (qparam('q') || '').trim();
    const $input = $('#searchQ');
    $input.val(q);
    $input.on('keydown', function (e) {
        if (e.key === 'Enter') {
            const v = $input.val().trim();
            window.location = '/search' + (v ? ('?q=' + encodeURIComponent(v)) : '');
        }
    });
    $input.trigger('focus');

    if (!q) { $('#searchCount').text('검색어를 입력하세요.'); return; }

    function group(icon, title, rows) {
        if (!rows.length) { return ''; }
        return `<section class="card result-group" style="margin-bottom:10px">
            <h4>${icon} ${esc(title)} (${rows.length})</h4>
            <div class="card-list">${rows.join('')}</div></section>`;
    }

    $.getJSON('/api/search', { q: q }).done(function (res) {
        const d = res.data || {};
        const projects = (d.projects || []).map(p =>
            `<a class="item" href="/projects/${p.id}" style="text-decoration:none;color:var(--fg)"><div class="body"><b>${esc(p.name)}</b> <span class="cat-tag">${esc(p.clientOrgName || '')}</span></div></a>`);
        const tcs = (d.testCases || []).map(t =>
            `<a class="item" href="/projects/${t.projectId}/test-cases/${t.id}" style="text-decoration:none;color:var(--fg)"><div class="body"><b style="font-family:var(--mono)">${esc(t.code)}</b> ${esc(t.title)}</div></a>`);
        const defects = (d.defects || []).map(x =>
            `<a class="item" href="/projects/${x.projectId}/defects/${x.id}" style="text-decoration:none;color:var(--fg)"><div class="body"><b style="font-family:var(--mono)">${esc(x.code)}</b> ${esc(x.title)}</div></a>`);

        const total = projects.length + tcs.length + defects.length;
        $('#searchCount').html(`<b>${total}건</b> 결과`);
        const html = group('📁', '프로젝트', projects) + group('📋', '테스트 케이스', tcs) + group('🐞', '결함', defects);
        $('#searchResults').html(html || '<p class="page-sub">검색 결과가 없습니다.</p>');
    }).fail(function () {
        $('#searchResults').html('<p class="err" style="color:var(--hot)">검색 실패</p>');
    });
})();
