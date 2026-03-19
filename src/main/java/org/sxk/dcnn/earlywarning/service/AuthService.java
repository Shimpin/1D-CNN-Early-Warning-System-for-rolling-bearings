package org.sxk.dcnn.earlywarning.service;

import org.springframework.stereotype.Service;
import org.sxk.dcnn.earlywarning.config.JwtProperties;
import org.sxk.dcnn.earlywarning.config.JwtUtil;
import org.sxk.dcnn.earlywarning.entity.User;
import org.sxk.dcnn.earlywarning.dao.UserRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtProperties jwtProperties;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, JwtProperties jwtProperties,
                       org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtProperties = jwtProperties;
        this.passwordEncoder = passwordEncoder;
    }

    /** 登录：校验用户名密码，返回 token 与用户信息 */
    public Map<String, Object> login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return fail("用户名和密码不能为空");
        }
        Optional<User> opt = userRepository.findByUsername(username.trim());
        if (opt.isEmpty() || !passwordEncoder.matches(password, opt.get().getPasswordHash())) {
            return fail("用户名或密码错误");
        }
        User user = opt.get();
        String token = JwtUtil.createToken(jwtProperties, user.getId(), user.getUsername());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("username", user.getUsername());
        data.put("userId", user.getId());
        return success(data);
    }

    /** 注册 */
    public Map<String, Object> register(String username, String password, String confirmPassword) {
        if (username == null || username.isBlank()) {
            return fail("用户名不能为空");
        }
        if (password == null || password.isBlank()) {
            return fail("密码不能为空");
        }
        if (!password.equals(confirmPassword)) {
            return fail("两次输入的密码不一致");
        }
        if (userRepository.findByUsername(username.trim()).isPresent()) {
            return fail("用户名已存在");
        }
        User user = new User();
        user.setUsername(username.trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        userRepository.save(user);
        return success(null);
    }

    private Map<String, Object> success(Object data) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", 200);
        m.put("msg", "操作成功");
        m.put("data", data);
        return m;
    }

    private Map<String, Object> fail(String msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", 400);
        m.put("msg", msg);
        m.put("data", null);
        return m;
    }
}
