(function () {
    'use strict';
    const $root = $('#settingsPage');
    if (!$root.length) { return; }
    const projectId = $root.data('project-id');
    const tier = String($root.data('tier') || '');
    const myId = String($root.data('uid') || '');
    const isTeam = tier === 'team';

    function esc(s) { return $('<div>').text(s == null ? '' : s).html(); }
    function errMessage(xhr, f) { const r = xhr.responseJSON; return (r && r.message) ? r.message : (f || `오류 (HTTP ${xhr.status})`); }
    function attr(k) { const v = $root.attr('data-' + k); return (v === undefined || v === 'null') ? '' : v; }

    // --- 기본 정보 ---
    function fillInfo() {
        const $f = $('#infoForm');
        $f.find('[name=name]').val(attr('name'));
        $f.find('[name=type]').val(attr('type'));
        $f.find('[name=description]').val(attr('desc'));
        $f.find('[name=startDate]').val(attr('start'));
        $f.find('[name=endDate]').val(attr('end'));
        if (!isTeam) { $f.find('input,select,textarea,button').prop('disabled', true); }
    }
    $('#infoForm').on('submit', function (e) {
        e.preventDefault();
        const $f = $(this); const $msg = $('#infoMsg');
        const payload = {
            name: $f.find('[name=name]').val().trim(), type: $f.find('[name=type]').val() || null,
            description: $f.find('[name=description]').val().trim() || null,
            startDate: $f.find('[name=startDate]').val() || null, endDate: $f.find('[name=endDate]').val() || null
        };
        $.ajax({ url: `/api/projects/${projectId}`, method: 'PATCH', contentType: 'application/json', data: JSON.stringify(payload) })
            .done(function () { $msg.css('color', 'var(--ok)').text('저장되었습니다.'); })
            .fail(function (xhr) { $msg.css('color', 'var(--hot)').text(errMessage(xhr, '저장 실패')); });
    });

    // --- 멤버 ---
    function loadMembers() {
        $.getJSON(`/api/projects/${projectId}/members`).done(function (res) {
            const members = (res && res.data) || [];
            $('#memberList').html(members.map(function (m) {
                const role = m.accountTier === 'client' ? '고객사' : (m.accountJob || m.accountTier);
                const rm = (isTeam && String(m.accountId) !== myId)
                    ? `<button class="btn sm danger" data-rm="${esc(m.accountId)}">제외</button>` : '';
                return `<div class="item" style="cursor:default"><span class="body"><b>${esc(m.accountName)}</b> · ${esc(m.accountEmail)} <span class="cat-tag">${esc(role)}</span></span>${rm}</div>`;
            }).join(''));
        });
    }
    if (isTeam) { $('#inviteBtn').prop('hidden', false); }
    $('#inviteBtn').on('click', function () { $('#inviteCard').prop('hidden', false); });
    $('#inviteCancel').on('click', function () { $('#inviteCard').prop('hidden', true); });
    $('#inviteForm').on('submit', function (e) {
        e.preventDefault();
        const $f = $(this); const $res = $('#inviteResult');
        const payload = { email: $f.find('[name=email]').val().trim(), inviteType: $f.find('[name=inviteType]').val() };
        $.ajax({ url: `/api/projects/${projectId}/members/invite`, method: 'POST', contentType: 'application/json', data: JSON.stringify(payload) })
            .done(function (res) {
                const d = res.data || {};
                if (d.type === 'added') {
                    $res.css('color', 'var(--ok)').text('기존 계정을 멤버로 추가했습니다.');
                } else {
                    $res.css('color', 'var(--accent)').html('초대 링크: <a href="' + esc(d.acceptUrl) + '">' + esc(d.acceptUrl) + '</a>');
                }
                $f.find('[name=email]').val(''); loadMembers();
            })
            .fail(function (xhr) { $res.css('color', 'var(--hot)').text(errMessage(xhr, '초대 실패')); });
    });
    $('#memberList').on('click', '[data-rm]', function () {
        const id = $(this).data('rm');
        if (!confirm('이 멤버를 제외할까요?')) { return; }
        $.ajax({ url: `/api/projects/${projectId}/members/${id}`, method: 'DELETE' })
            .done(loadMembers).fail(function (xhr) { alert(errMessage(xhr, '제외 실패')); });
    });

    // --- Danger Zone ---
    if (!isTeam) { $('#dangerCard').prop('hidden', true); }
    $('#archiveBtn').on('click', function () {
        if (!confirm('프로젝트를 보관할까요?')) { return; }
        const $msg = $('#dangerMsg');
        $.ajax({ url: `/api/projects/${projectId}/archive`, method: 'POST' })
            .done(function () { $msg.css('color', 'var(--ok)').text('보관되었습니다.'); })
            .fail(function (xhr) { $msg.css('color', 'var(--hot)').text(errMessage(xhr, '보관 실패')); });
    });
    $('#deleteBtn').on('click', function () {
        const name = prompt('삭제하려면 프로젝트명을 정확히 입력하세요:');
        if (name == null) { return; }
        $.ajax({ url: `/api/projects/${projectId}`, method: 'DELETE', contentType: 'application/json', data: JSON.stringify({ confirmName: name }) })
            .done(function () { window.location.href = '/'; })
            .fail(function (xhr) { $('#dangerMsg').css('color', 'var(--hot)').text(errMessage(xhr, '삭제 실패')); });
    });

    $(function () { fillInfo(); loadMembers(); });
})();
