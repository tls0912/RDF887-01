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
 * 驗證服務
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
