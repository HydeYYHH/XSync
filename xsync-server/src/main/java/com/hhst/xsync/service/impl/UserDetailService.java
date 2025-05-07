package com.hhst.xsync.service.impl;

import com.hhst.xsync.service.IUserService;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailService implements UserDetailsService {

  @Autowired IUserService userService;

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    var user = userService.getById(email);
    if (user == null) {
      throw new UsernameNotFoundException(email);
    }
    String role = Optional.ofNullable(user.getRole()).orElse("ROLE_ANONYMOUS");
    return new User(email, user.getPassword(), List.of(new SimpleGrantedAuthority(role)));
  }
}
