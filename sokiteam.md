# SokiTeam — Tài liệu Permissions

Plugin Minecraft (Paper, api-version 1.21, hỗ trợ Folia). Hệ thống quyền chia làm **2 nhóm độc lập**:

1. **Admin permissions** — node Bukkit cấp ở cấp server (cho staff/op), dùng cho Admin GUI.
2. **Team permissions** — quyền chi tiết theo từng hành động *bên trong* một team, gán theo role.

---

## 1. Admin Permissions (Bukkit nodes)

Khai báo tại `AdminPermission.java`. Node `sokiteam.admin` là **umbrella node** — ai giữ node này sẽ ngầm có tất cả node con. Op của server cũng mặc định có toàn bộ.

> Lưu ý: Paper plugin không thể khai báo permission trong YAML, nên không có defaults registration — không giữ node nghĩa là "không được cấp".

| Node | Mô tả |
|------|-------|
| `sokiteam.admin` | Umbrella node. Ngầm cấp mọi node con bên dưới. |
| `sokiteam.admin.view` | Mở Admin GUI và xem dữ liệu team (chỉ đọc). |
| `sokiteam.admin.search` | Tìm kiếm team / người chơi từ Admin GUI. |
| `sokiteam.admin.edit` | Sửa dữ liệu team: đổi tên, tag, role, thành viên, cài đặt. |
| `sokiteam.admin.delete` | Hành động phá hủy: giải tán, reset dữ liệu, xóa toàn bộ thành viên. |
| `sokiteam.admin.logs` | Xem lịch sử audit-log. |
| `sokiteam.admin.bank` | Điều chỉnh số dư ngân hàng của team. |
| `sokiteam.admin.home` | Đặt / xóa / xem home của team. |
| `sokiteam.admin.chest` | Reset / kiểm tra rương team. |
| `sokiteam.admin.relation` | Quản lý quan hệ đồng minh / kẻ thù / trung lập. |

---

## 2. Team Permissions (theo role)

Khai báo tại `TeamPermission.java`. Đây là các quyền hành động bên trong team, gán theo role. Có thể **ghi đè qua config** lúc khởi động (`PermissionManager.setRolePermissions`).

### Danh sách đầy đủ quyền

**Quản lý thành viên & team**

| Permission | Mô tả |
|------------|-------|
| `INVITE_MEMBER` | Mời thành viên mới. |
| `KICK_MEMBER` | Đuổi thành viên. |
| `MANAGE_ROLES` | Quản lý / gán role. |
| `REVIEW_INVITES` | Duyệt lời mời. |
| `REVIEW_JOIN_REQUESTS` | Duyệt yêu cầu xin vào. |
| `RENAME_TEAM` | Đổi tên team. |
| `EDIT_SETTINGS` | Chỉnh cài đặt team. |
| `DISBAND_TEAM` | Giải tán team. |
| `TRANSFER_LEADER` | Chuyển quyền chủ team. |
| `MANAGE_MEMBERS` | Quản lý thành viên (tổng quát). |
| `MANAGE_RELATIONS` | Quản lý đồng minh / kẻ thù. |
| `TOGGLE_TEAM_CHAT` | Bật/tắt chat team. |

**Rương team (Chest)**

| Permission | Mô tả |
|------------|-------|
| `CHEST_OPEN` | Mở rương team. |
| `CHEST_DEPOSIT` | Bỏ đồ vào rương. |
| `CHEST_WITHDRAW` | Lấy đồ ra khỏi rương. |
| `CHEST_UPGRADE` | Nâng cấp rương. |

**Home (điểm về)**

| Permission | Mô tả |
|------------|-------|
| `SET_HOME` | Đặt home cho team. |
| `TELEPORT_HOME` | Dịch chuyển về home. |
| `DELETE_HOME` | Xóa home. |

**Ngân hàng**

| Permission | Mô tả |
|------------|-------|
| `WITHDRAW_BALANCE` | Rút tiền từ quỹ team. |

---

## 3. Roles & quyền mặc định

Khai báo tại `TeamRole.java`. Sắp xếp theo trọng số (weight) giảm dần. Role weight cao hơn mới quản lý được role thấp hơn. **Leader / OWNER luôn có toàn bộ quyền** bất kể cấu hình.

| Role | Weight | Tên hiển thị | Quyền mặc định |
|------|--------|--------------|----------------|
| `OWNER` | 100 | Chủ Team | **Tất cả** quyền |
| `CO_OWNER` | 80 | Phó Chủ | Tất cả **trừ** `DISBAND_TEAM`, `TRANSFER_LEADER` |
| `ADMIN` | 60 | Quản trị | Xem chi tiết bên dưới |
| `MOD` | 40 | Điều hành | Xem chi tiết bên dưới |
| `MEMBER` | 20 | Thành viên | Xem chi tiết bên dưới |
| `RECRUIT` | 10 | Tân binh | Xem chi tiết bên dưới |

### Chi tiết quyền từng role

**ADMIN (60)**

- `INVITE_MEMBER`, `KICK_MEMBER`
- `REVIEW_INVITES`, `REVIEW_JOIN_REQUESTS`
- `EDIT_SETTINGS`
- `CHEST_OPEN`, `CHEST_DEPOSIT`, `CHEST_WITHDRAW`, `CHEST_UPGRADE`
- `SET_HOME`, `TELEPORT_HOME`
- `MANAGE_RELATIONS`, `TOGGLE_TEAM_CHAT`
- `MANAGE_MEMBERS`, `WITHDRAW_BALANCE`

*(Không có: `MANAGE_ROLES`, `RENAME_TEAM`, `DISBAND_TEAM`, `TRANSFER_LEADER`, `DELETE_HOME`)*

**MOD (40)**

- `INVITE_MEMBER`, `KICK_MEMBER`
- `REVIEW_INVITES`, `REVIEW_JOIN_REQUESTS`
- `CHEST_OPEN`, `CHEST_DEPOSIT`, `CHEST_WITHDRAW`
- `TELEPORT_HOME`
- `TOGGLE_TEAM_CHAT`
- `WITHDRAW_BALANCE`

**MEMBER (20)**

- `CHEST_OPEN`, `CHEST_DEPOSIT`, `CHEST_WITHDRAW`
- `TELEPORT_HOME`
- `TOGGLE_TEAM_CHAT`

**RECRUIT (10)**

- `CHEST_OPEN`, `CHEST_DEPOSIT`
- `TELEPORT_HOME`

---

## 4. Cách phân giải quyền (resolution)

Tại `PermissionManager.java`:

- **Leader** của team: luôn có **mọi** quyền.
- Thành viên có role `OWNER`: luôn có mọi quyền.
- Thành viên khác: có quyền nếu role của họ chứa permission đó.
- Quyền theo role có thể bị **ghi đè qua config** lúc khởi động.

Đối với Admin nodes: kiểm tra qua `AdminPermission.held()` — giữ node trực tiếp **hoặc** giữ `sokiteam.admin` đều được tính là có quyền.
