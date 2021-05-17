package org.anti.seckill.service;

import org.anti.seckill.error.BusinessException;
import org.anti.seckill.service.model.UserModel;
import org.springframework.dao.DuplicateKeyException;

public interface UserService {

    UserModel getUserById(Integer id);

    UserModel getUserByIdInCache(Integer id);

    void register(UserModel userModel) throws BusinessException;

    UserModel validateLogin(String telphone, String encrptPassword) throws BusinessException;
}