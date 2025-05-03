package com.hhst.xsync.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hhst.xsync.entity.User;
import com.hhst.xsync.mapper.UserMapper;
import com.hhst.xsync.service.IUserService;
import org.springframework.stereotype.Service;


@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

}
