(function () {
    var container = document.getElementById('releases-list');
    if (!container) return;

    var API = 'https://api.github.com/repos/plsmeow/ancient';
    var CACHE_KEY = 'ancient-releases-v3';
    var CACHE_TIME = 15 * 60 * 1000;

    // 1) Show cached or fallback data IMMEDIATELY (synchronous)
    var initialData = readCache(false) || createFallbackData();
    renderReleases(initialData.releases, initialData.messages || {});

    // 2) Try to update from GitHub in the background
    loadFromGitHub().then(function (fresh) {
        saveCache(fresh);
        renderReleases(fresh.releases, fresh.messages || {});
    }).catch(function () {
        // Already showing fallback/cached data, nothing to do
    });

    function loadFromGitHub() {
        return Promise.all([
            fetch(API + '/releases?per_page=100'),
            fetch(API + '/commits?per_page=100')
        ]).then(function (responses) {
            if (!responses[0].ok || !responses[1].ok) {
                throw new Error('GitHub API: ' + responses[0].status + '/' + responses[1].status);
            }
            return Promise.all([responses[0].json(), responses[1].json()]);
        }).then(function (results) {
            var releases = results[0];
            var commits = results[1];
            var messages = {};
            commits.forEach(function (item) {
                if (item.sha && item.commit && item.commit.message) {
                    messages[item.sha.substring(0, 7)] = item.commit.message.split('\n')[0];
                }
            });
            return { releases: releases, messages: messages };
        });
    }

    function renderReleases(releases, messages) {
        if (!releases || !releases.length) {
            container.innerHTML = '<div class="releases-loading">нет релизов</div>';
            return;
        }

        container.innerHTML = '';

        releases.forEach(function (release, index) {
            var tag = release.tag_name || '';
            var name = release.name || tag;
            var hash = extractCommitHash(name);
            var asset = (release.assets || []).find(function (item) {
                return item.name && item.name.toLowerCase().endsWith('.jar');
            });
            var downloadUrl = asset
                ? asset.browser_download_url
                : 'https://github.com/plsmeow/ancient/releases/download/' + encodeURIComponent(tag) + '/onetap-1.0.0.jar';
            var message = release.body || messages[hash] || '';
            var size = asset && asset.size ? formatSize(asset.size) : '';
            var card = document.createElement('article');

            card.className = 'release-card' + (index === 0 ? ' latest' : '');
            card.style.animationDelay = (0.06 * index) + 's';
            card.innerHTML =
                '<div class="release-info">' +
                    '<div class="release-header">' +
                        '<div class="release-name">' + escapeHtml(name) +
                            '<span class="release-tag">' + escapeHtml(tag) + '</span>' +
                            (index === 0 ? '<span class="release-latest-badge">latest</span>' : '') +
                        '</div>' +
                    '</div>' +
                    (message ? '<div class="release-body">' + escapeHtml(firstLine(message)) + '</div>' : '') +
                '</div>' +
                '<a class="release-download" href="' + escapeHtml(downloadUrl) + '">' +
                    '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="M2.75 14A1.75 1.75 0 0 1 1 12.25v-2.5a.75.75 0 0 1 1.5 0v2.5c0 .138.112.25.25.25h10.5a.25.25 0 0 0 .25-.25v-2.5a.75.75 0 0 1 1.5 0v2.5A1.75 1.75 0 0 1 13.25 14ZM7.25 7.689V2a.75.75 0 0 1 1.5 0v5.689l1.97-1.969a.749.749 0 1 1 1.06 1.06l-3.25 3.25a.749.749 0 0 1-1.06 0L4.22 6.78a.749.749 0 1 1 1.06-1.06l1.97 1.969Z"/></svg>' +
                    'скачать' +
                '</a>';

            container.appendChild(card);
        });
    }

    function readCache(ignoreAge) {
        try {
            var cache = JSON.parse(localStorage.getItem(CACHE_KEY));
            if (!cache || !cache.data) return null;
            if (!ignoreAge && Date.now() - cache.savedAt > CACHE_TIME) return null;
            return cache.data;
        } catch (error) {
            return null;
        }
    }

    function saveCache(data) {
        try {
            localStorage.setItem(CACHE_KEY, JSON.stringify({ savedAt: Date.now(), data: data }));
        } catch (error) {
            // localStorage might be unavailable
        }
    }

    function createFallbackData() {
        var items = [
            ['v1.0.0-40', 'b76247a', 'улучшен web'],
            ['v1.0.0-39', '6d9b0bb', 'Criticals Grim улучшен'],
            ['v1.0.0-38', 'c4d56d1', '1'],
            ['v1.0.0-37', '04d1627', 'Улучшена Neuro'],
            ['v1.0.0-36', 'f878402', 'Neuro aura тест'],
            ['v1.0.0-35', '7dc10d8', 'фиксы'],
            ['v1.0.0-34', '0fe817e', 'фиксы'],
            ['v1.0.0-33', '36ea129', 'tploot, blockesp fix, bind fix, hotbar refill, новая система обходов guimove'],
            ['v1.0.0-32', '85c59a3', 'Пофикшен grim guimove и улучшен scaffold'],
            ['v1.0.0-31', 'f8232d0', 'guimove grim']
        ];
        var messages = {};
        var releases = items.map(function (item) {
            messages[item[1]] = item[2];
            return { tag_name: item[0], name: 'release-' + item[1], assets: [] };
        });
        return { releases: releases, messages: messages };
    }

    function extractCommitHash(name) {
        var match = String(name).match(/release-([0-9a-f]{7,})/i);
        return match ? match[1].substring(0, 7) : '';
    }

    function firstLine(text) {
        return String(text || '').trim().split('\n')[0];
    }

    function formatSize(bytes) {
        return bytes < 1048576
            ? (bytes / 1024).toFixed(1) + ' KB'
            : (bytes / 1048576).toFixed(1) + ' MB';
    }

    function escapeHtml(value) {
        var div = document.createElement('div');
        div.textContent = String(value || '');
        return div.innerHTML;
    }
})();
