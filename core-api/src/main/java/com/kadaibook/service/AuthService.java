package com.kadaibook.service;

import com.kadaibook.domain.Role;
import com.kadaibook.domain.User;
import com.kadaibook.repository.UserRepository;
import com.kadaibook.security.JwtService;
import com.kadaibook.web.ApiException;
import com.kadaibook.web.dto.AuthDtos.AuthResponse;
import com.kadaibook.web.dto.AuthDtos.LoginRequest;
import com.kadaibook.web.dto.AuthDtos.RegisterRequest;
import com.kadaibook.web.dto.AuthDtos.UserDto;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String phone = normalisePhone(request.phone());
        if (phone == null) {
            throw ApiException.badRequest("invalid_phone", "Enter a valid phone number.");
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.badRequest("invalid_password", "The password is too long.");
        }
        if (userRepository.existsByPhone(phone)) {
            throw ApiException.conflict("phone_taken", "An account with this phone already exists.");
        }
        User user = new User();
        user.setPhone(phone);
        user.setDisplayName(request.displayName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.SHOPKEEPER);
        user = userRepository.save(user);
        return response(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String phone = normalisePhone(request.phone());
        User user = phone == null ? null : userRepository.findByPhone(phone).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("The phone number or password is incorrect.");
        }
        return response(user);
    }

    private AuthResponse response(User user) {
        JwtService.IssuedToken token = jwtService.issue(user);
        return new AuthResponse(
                token.token(),
                token.expiresAt(),
                new UserDto(user.getId(), user.getPhone(), user.getDisplayName(), user.getRole().name()));
    }

    static String normalisePhone(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim().replaceAll("[\\s-]", "");
        if (!trimmed.matches("\\+?[0-9]{8,15}")) {
            return null;
        }
        return trimmed;
    }
}
