# Auth 與權限規格

## 文件資訊

- 建立日期：2026-06-24
- 適用範圍：`AuthController`、`AuthService`、`UsersRepository`、`RolesMapper`

## 目的

本文件說明目前登入與角色查詢流程。現有實作以 username/password 查詢使用者，驗證成功後回傳 userId、username、roleName。

## 登入流程

```text
POST /api/auth/login
  -> AuthController
  -> AuthService.login(...)
  -> UsersRepository.findByUsername(...)
  -> RolesMapper.selectById(...)
  -> LoginResponse
```

## 維護規則

1. 密碼驗證目前為明文比對；若進入正式權限控管，應改為雜湊驗證。
2. 權限細節若擴充，應補 `permissions`、`role_permissions` 查詢與 API 文件。
3. Controller 不應直接查 DB，維持透過 `AuthService`。
