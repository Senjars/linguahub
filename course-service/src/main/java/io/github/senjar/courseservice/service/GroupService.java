package io.github.senjar.courseservice.service;

import io.github.senjar.courseservice.dto.group.CreateGroupDto;
import io.github.senjar.courseservice.dto.group.GroupDto;
import io.github.senjar.courseservice.dto.group.UpdateGroupDto;
import java.util.List;

public interface GroupService {

    GroupDto createGroup(CreateGroupDto createGroupDto);

    GroupDto updateGroup(UpdateGroupDto updateGroupDto, Long groupId);

    GroupDto findGroup(Long groupId);

    List<GroupDto> getGroups();

    void deleteGroup(Long groupId);
}
