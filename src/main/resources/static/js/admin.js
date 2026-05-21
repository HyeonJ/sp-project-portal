(function () {
    'use strict';
    if (!$('#adminPage').length) { return; }

    function esc(s) { return $('<div>').text(s == null ? '' : s).html(); }
    function errMessage(xhr, f) { const r = xhr.responseJSON; return (r && r.message) ? r.message : (f || `오류 (HTTP ${xhr.status})`); }
    function fmtDate(iso) {
        if (!iso) { return ''; }
        const d = new Date(iso); if (isNaN(d)) { return ''; }
        const p = (n) => String(n).padStart(2, '0');
        return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
    }

    let currentTier = 'team';

    function statusPill(s) {
        const m = { active: ['ok', '활성'], inactive: ['hot', '비활성'], pending: ['warn', '대기'] };
        const x = m[s] || ['muted', s];
        return `<span class="pill ${x[0]}">${esc(x[1])}</span>`;
    }

    function loadAccounts() {
        $('.acct-tab').removeClass('primary');
        $(`.acct-tab[data-tier=${currentTier}]`).addClass('primary');
        $.getJSON('/api/admin/accounts', { tier: currentTier }).done(function (res) {
            const accounts = (res && res.data) || [];
            const $list = $('#accountList');
            if (!accounts.length) { $list.html('<p class="page-sub" style="margin:0">계정이 없습니다.</p>'); return; }
            $list.html(accounts.map(function (a) {
                const next = a.status === 'active' ? 'inactive' : 'active';
                const toggle = `<button class="btn sm" data-acct="${esc(a.id)}" data-next="${next}">${next === 'inactive' ? '비활성화' : '활성화'}</button>`;
                const meta = a.job ? esc(a.job) : (a.clientOrgId ? '고객사' : '관리자');
                return `<div class="item" style="cursor:default"><span class="body"><b>${esc(a.name)}</b> · ${esc(a.email)} <span class="cat-tag">${meta}</span></span>
                    <span style="display:flex;gap:10px;align-items:center">${statusPill(a.status)}${toggle}</span></div>`;
            }).join(''));
        });
    }

    function loadProjects() {
        $.getJSON('/api/admin/projects').done(function (res) {
            const projects = (res && res.data) || [];
            $('#projCount').text(`${projects.length}개`);
            $('#adminProjects').html(projects.length
                ? projects.map(p => `<a class="item" href="/projects/${p.id}" style="text-decoration:none;color:var(--fg)"><span class="body"><b>${esc(p.name)}</b> · ${esc(p.clientOrgName)}</span><span class="pill accent">단계 ${esc(p.currentStage)}</span></a>`).join('')
                : '<p class="page-sub" style="margin:0">프로젝트가 없습니다.</p>');
        });
    }

    function loadAudit() {
        $.getJSON('/api/admin/audit').done(function (res) {
            const logs = (res && res.data) || [];
            $('#auditList').html(logs.length
                ? logs.map(l => `<div class="item" style="cursor:default"><span class="body"><b style="font-family:var(--mono)">${esc(l.action)}</b> · ${esc(l.actorName || '시스템')} <span class="cat-tag">${esc(l.actorRole)}</span> ${esc(l.target || '')}</span><span class="t">${esc(fmtDate(l.createdAt))}</span></div>`).join('')
                : '<p class="page-sub" style="margin:0">감사 로그가 없습니다.</p>');
        });
    }

    $('.acct-tab').on('click', function () { currentTier = $(this).data('tier'); loadAccounts(); });
    $('#newAccountBtn').on('click', function () { $('#newAccountCard').prop('hidden', false); });
    $('#accountCancel').on('click', function () { $('#newAccountCard').prop('hidden', true); });

    $('#accountList').on('click', '[data-acct]', function () {
        const id = $(this).data('acct'); const next = $(this).data('next');
        $.ajax({ url: `/api/admin/accounts/${id}/status`, method: 'PATCH', contentType: 'application/json', data: JSON.stringify({ status: next }) })
            .done(loadAccounts).fail(function (xhr) { alert(errMessage(xhr, '변경 실패')); });
    });

    $('#accountForm').on('submit', function (e) {
        e.preventDefault();
        const $f = $(this); const $err = $('#accountErr').hide();
        const payload = {
            email: $f.find('[name=email]').val().trim(), name: $f.find('[name=name]').val().trim(),
            tier: $f.find('[name=tier]').val(), job: $f.find('[name=job]').val(),
            clientOrgName: $f.find('[name=clientOrgName]').val().trim() || null
        };
        $.ajax({ url: '/api/admin/accounts', method: 'POST', contentType: 'application/json', data: JSON.stringify(payload) })
            .done(function () { $('#newAccountCard').prop('hidden', true); $f[0].reset(); currentTier = payload.tier; loadAccounts(); loadAudit(); })
            .fail(function (xhr) { $err.text(errMessage(xhr, '생성 실패')).show(); });
    });

    $(function () { loadAccounts(); loadProjects(); loadAudit(); });
})();
