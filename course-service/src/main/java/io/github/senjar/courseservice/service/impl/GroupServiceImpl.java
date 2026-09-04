package io.github.senjar.courseservice.service.impl;

import io.github.senjar.courseservice.dto.group.CreateGroupDto;
import io.github.senjar.courseservice.dto.group.GroupDto;
import io.github.senjar.courseservice.dto.group.UpdateGroupDto;
import io.github.senjar.courseservice.exception.EntityNotFoundException;
import io.github.senjar.courseservice.mapper.GroupMapper;
import io.github.senjar.courseservice.model.Group;
import io.github.senjar.courseservice.repository.GroupRepository;
import io.github.senjar.courseservice.service.GroupService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private final GroupRepository groupRepository;
    private final GroupMapper groupMapper;

    @Override
    public GroupDto createGroup(CreateGroupDto createGroupDto) {
        Group group = groupMapper.toEntity(createGroupDto);
        Group savedGroup = groupRepository.save(group);

        return groupMapper.toDto(savedGroup);
    }

    @Override
    public GroupDto updateGroup(UpdateGroupDto updateGroupDto, Long groupId) {
        Group group = groupRepository.findById(groupId).orElseThrow(
                () -> new EntityNotFoundException(""));

        groupMapper.updateGroup(updateGroupDto, group);
        Group savedGroup = groupRepository.save(group);

        return groupMapper.toDto(savedGroup);
    }

    @Override
    public GroupDto findGroup(Long groupId) {
        Group group = groupRepository.findById(groupId).orElseThrow(
                () -> new EntityNotFoundException(""));

        return groupMapper.toDto(group);
    }

    @Override
    public List<GroupDto> getGroups() {
        return groupRepository.findAll().stream().map(groupMapper::toDto).toList();
    }

    @Override
    public void deleteGroup(Long groupId) {
        Group group = groupRepository.findById(groupId).orElseThrow(
                () -> new EntityNotFoundException(""));

        groupRepository.delete(group);
    }
}
