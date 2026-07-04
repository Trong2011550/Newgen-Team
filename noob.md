# noob.md — bộ nhớ dự án NewGen Team

## Rules
- Build/verify: `.\gradlew.bat build` (Windows PowerShell, timeout ~300s). Fat jar ra `build\libs\` (shadowJar, relocate `me.newgen.team.libs`).
- KHÔNG dùng `Set-Content -Encoding UTF8` trên file .java — PowerShell 5 ghi BOM làm javac fail (`illegal character '\ufeff'`). Luôn dùng `[System.IO.File]::WriteAllText($path, $t, (New-Object System.Text.UTF8Encoding($false)))`.
- Package gốc: `me.newgen.team` (đã rebrand từ `com.trong.sokiteam`). Main: `NewGenTeamPlugin`, bootstrapper: `NewGenTeamBootstrap` (Paper plugin, `paper-plugin.yml`).
- Permission prefix: `newgenteam.*`. Command `/team` alias `t/faction/f/guild/g`. PlaceholderAPI identifier: `newgenteam`. DB file: `newgenteam.db`.

## Notes
- Dự án KHÔNG có git — mọi thao tác move/xóa file là không thể hoàn tác. Khi move hàng loạt, verify đủ số file trước/sau (74 file .java).
- Sự cố đã gặp: move `com/trong/sokiteam` → `me/newgen/team` làm mất 10 file (9 model + util/Inventories). Đã khôi phục bằng decompile CFR từ `build\classes\java\main` cũ. Code model hiện là bản decompile — nên review/làm sạch khi refactor Phase 4 (storage layer).
- Plugin chưa phát hành, DB trống → được tự do đổi schema, không cần migration.
- Kế hoạch refactor premium theo phase: 1 Rebrand (XONG) → 2 Theme+Language (XONG, build xanh 2026-07-04) → 3 Config split → 4 Storage layer đa DB → 5 GUI config-driven → 6 API/Events/Hooks/Placeholder → 7 Reload+Logging+README.
- Phase 2: ThemeManager (theme.yml, token <primary>/<secondary>/<accent>/<muted>/<danger>/<success>/<warning>/<info>) + LanguageManager (lang/vi_VN.yml, en_US.yml, fallback en_US, chọn qua `language:` trong config.yml). MessageManager giờ là facade Theme+Language, parse MiniMessage.
- `Text.auto()`: transitional — có tag `<` thì parse MiniMessage, không thì legacy &. `messages.yml` cũ chưa xóa (không còn được load) — dọn ở Phase 3.
- 2026-07-05: Toàn bộ text GUI (player + admin, ~33 menu) đã migrate sang lang key `gui.*` (vi_VN + en_US), không còn chuỗi tiếng Việt/legacy hardcode trong `gui/` (trừ TopMenu rank #1 dùng Text.galaxy hiệu ứng đặc biệt). Build xanh. Phase 5 chỉ còn phần GUI layout config-driven (menus/*.yml).
- `Inventories.java` dùng deprecated API (cảnh báo javac) — xử lý khi refactor, không chặn build.
- Text hiển thị đã đổi "SokiTeam" → "NewGen Team" (MessageManager prefix, AdminMainMenu, plugin log).
