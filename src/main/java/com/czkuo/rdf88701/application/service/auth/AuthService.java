package com.czkuo.rdf88701.application.service.auth;

import com.czkuo.rdf88701.application.dto.auth.LoginRequest;
import com.czkuo.rdf88701.application.dto.auth.LoginResponse;
import com.czkuo.rdf88701.domain.repository.UsersRepository;
import com.czkuo.rdf88701.infra.entity.Users;
import com.czkuo.rdf88701.infra.mapper.RolesMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 使用者登入驗證服務。
 *
 * <p>依 username 查詢 users，驗證 password 後查詢角色名稱並組成 LoginResponse。
 * 目前實作為明文比對，正式權限化時應改為雜湊驗證。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsersRepository usersRepository;
    private final RolesMapper rolesMapper;

    /**
     * 驗證使用者登入
     */
    public LoginResponse login(LoginRequest request) {
        Optional<Users> userOpt = usersRepository.findByUsername(request.getUsername());

        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }

        Users user = userOpt.get();

        if (!user.getPassword().equals(request.getPassword())) {
            throw new IllegalArgumentException("Incorrect password");
        }

        // 查找角色名稱
        String roleName = rolesMapper.selectById(user.getRoleId()).getName();

        LoginResponse response = new LoginResponse();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setRoleName(roleName);
        return response;
    }
}
