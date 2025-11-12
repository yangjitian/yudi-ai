package com.yudi.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yudi.ai.model.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {

}
