package com.hhst.xsync.controller;

import com.hhst.xsync.dto.Response;
import com.hhst.xsync.entity.User;
import com.hhst.xsync.service.IUserService;
import com.hhst.xsync.utils.JwtUtils;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {

  @Autowired private IUserService userService;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JwtUtils jwtUtils;

  @PostMapping("/login")
  public Response login(@NotNull String email, @NotNull String password) {
    User user = userService.getById(email);
    if (passwordEncoder.matches(password, user.getPassword())) {
      return Response.build(HttpStatus.OK, "Login successful", jwtUtils.generateToken(email));
    } else {
      return Response.build(HttpStatus.UNAUTHORIZED, "Login failed");
    }
  }

  @PostMapping("/register")
  public Response register(@NotNull String email, @NotNull String password) {
    if (userService.save(new User(email, passwordEncoder.encode(password)))) {
      return Response.build(HttpStatus.OK, "Register successful", jwtUtils.generateToken(email));
    } else {
      return Response.build(HttpStatus.UNAUTHORIZED, "Register failed");
    }
  }
}
