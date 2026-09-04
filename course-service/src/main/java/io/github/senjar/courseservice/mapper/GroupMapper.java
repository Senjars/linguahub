package io.github.senjar.courseservice.mapper;

import io.github.senjar.courseservice.dto.group.CreateGroupDto;
import io.github.senjar.courseservice.dto.group.GroupDto;
import io.github.senjar.courseservice.dto.group.UpdateGroupDto;
import io.github.senjar.courseservice.model.Group;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface GroupMapper {

    Group toEntity(CreateGroupDto createGroupDto);

    GroupDto toDto(Group group);

    void updateGroup(UpdateGroupDto updateGroupDto, @MappingTarget Group group);
}
