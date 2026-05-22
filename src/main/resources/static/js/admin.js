(function () {
    'use strict';

    function esc(s) { return $('<div>').text(s == null ? '' : s).html(); }
    function errMessage(xhr, f) { const r = xhr.responseJSON; return (r && r.message) ? r.message : (f || `오류 (HTTP ${xhr.status})`); }
    function fmtDate(iso) {
        if (!iso) { return ''; }
        const d = new Date(iso); if (isNaN(d)) { return ''; }
        const p = (n) => String(n).padStart(2, '0');
        return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
    }
    function statusPill(s) {
        const m = { active: ['ok', '활성'], inactive: ['muted', '비활성'], pending: ['warn', '초대 대기'] };
        const x = m[s] || ['muted', s];
        return `<span class="pill ${x[0]}">${esc(x[1])}</span>`;
    }
    function reload() { window.location.reload(); }

    function toggleStatus(id, next) {
        $.ajax({ url: `/api/admin/accounts/${id}/status`, method: 'PATCH', contentType: 'application/json', data: JSON.stringify({ status: next }) })
            .done(reload).fail(function (xhr) { alert(errMessage(xhr, '상태 변경 실패')); });
    }

    function createAccount(payload, $err) {
        $.ajax({ url: '/api/admin/accounts', method: 'POST', contentType: 'application/json', data: JSON.stringify(payload) })
            .done(reload).fail(function (xhr) { $err.text(errMessage(xhr, '생성 실패')).show(); });
    }

    // --- 프로젝트팀 계정 ---
    if ($('#adminTeams').length) {
        $.getJSON('/api/admin/accounts', { tier: 'team' }).done(function (res) {
            const rows = ((res && res.data) || []).map(function (a) {
                const next = a.status === 'active' ? 'inactive' : 'active';
                return `<tr>
                    <td><b>${esc(a.name)}</b></td><td>${esc(a.email)}</td><td>${esc(a.job || '-')}</td>
                    <td>${statusPill(a.status)}</td>
                    <td class="actions"><button class="btn sm ${a.status === 'active' ? 'danger' : 'ok'}" data-acct="${esc(a.id)}" data-next="${next}">${a.status === 'active' ? '비활성화' : '활성화'}</button></td></tr>`;
            }).join('');
            $('#adminTeamsBody').html(rows || '<tr><td colspan="5" style="text-align:center;color:var(--muted);padding:24px">계정이 없습니다.</td></tr>');
        });
        $('#newTeamBtn').on('click', function () { $('#newTeamCard').prop('hidden', false); });
        $('#teamCancel').on('click', function () { $('#newTeamCard').prop('hidden', true); });
        $('#teamForm').on('submit', function (e) {
            e.preventDefault();
            const f = this; const $err = $('#teamErr').hide();
            if (!f.email.value.trim() || !f.name.value.trim()) { $err.text('이메일과 이름을 입력하세요.').show(); return; }
            createAccount({ email: f.email.value.trim(), name: f.name.value.trim(), tier: 'team', job: f.job.value }, $err);
        });
        $('#adminTeamsBody').on('click', '[data-acct]', function () { toggleStatus($(this).data('acct'), $(this).data('next')); });
    }

    // --- 고객사 계정 ---
    if ($('#adminClients').length) {
        $.getJSON('/api/admin/accounts', { tier: 'client' }).done(function (res) {
            const rows = ((res && res.data) || []).map(function (a) {
                const next = a.status === 'active' ? 'inactive' : 'active';
                return `<tr>
                    <td><b>${esc(a.name)}</b></td><td>${esc(a.email)}</td>
                    <td>${statusPill(a.status)}</td>
                    <td class="actions"><button class="btn sm ${a.status === 'active' ? 'danger' : 'ok'}" data-acct="${esc(a.id)}" data-next="${next}">${a.status === 'active' ? '비활성화' : '활성화'}</button></td></tr>`;
            }).join('');
            $('#adminClientsBody').html(rows || '<tr><td colspan="4" style="text-align:center;color:var(--muted);padding:24px">계정이 없습니다.</td></tr>');
        });
        $('#newClientBtn').on('click', function () { $('#newClientCard').prop('hidden', false); });
        $('#clientCancel').on('click', function () { $('#newClientCard').prop('hidden', true); });
        $('#clientForm').on('submit', function (e) {
            e.preventDefault();
            const f = this; const $err = $('#clientErr').hide();
            if (!f.email.value.trim() || !f.name.value.trim() || !f.clientOrgName.value.trim()) { $err.text('이메일·이름·고객사명을 입력하세요.').show(); return; }
            createAccount({ email: f.email.value.trim(), name: f.name.value.trim(), tier: 'client', clientOrgName: f.clientOrgName.value.trim() }, $err);
        });
        $('#adminClientsBody').on('click', '[data-acct]', function () { toggleStatus($(this).data('acct'), $(this).data('next')); });
    }

    // --- 감사 로그 ---
    if ($('#adminAudit').length) {
        $.getJSON('/api/admin/audit').done(function (res) {
            const rows = ((res && res.data) || []).map(function (l) {
                return `<tr>
                    <td style="font-family:var(--mono);font-size:12px">${esc(fmtDate(l.createdAt))}</td>
                    <td>${esc(l.actorName || '시스템')}</td><td>${esc(l.actorRole || '-')}</td>
                    <td>${esc(l.action)}</td><td><code>${esc(l.target || '')}</code></td></tr>`;
            }).join('');
            $('#adminAuditBody').html(rows || '<tr><td colspan="5" style="text-align:center;color:var(--muted);padding:24px">감사 로그가 없습니다.</td></tr>');
        });
    }
})();
