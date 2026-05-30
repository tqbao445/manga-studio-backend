package com.mangaflow.studio.dto.auth.mapper;

import com.mangaflow.studio.dto.auth.response.UserDTO;
import com.mangaflow.studio.model.auth.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "role", expression = "java(user.getRole().name())")
    UserDTO toDTO(User user);
}
