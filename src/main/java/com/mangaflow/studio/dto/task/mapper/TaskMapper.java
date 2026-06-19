package com.mangaflow.studio.dto.task.mapper;

import com.mangaflow.studio.dto.task.response.RegionBasicDTO;
import com.mangaflow.studio.dto.task.response.TaskResponse;
import com.mangaflow.studio.dto.task.response.UserBasicDTO;
import com.mangaflow.studio.model.auth.User;
import com.mangaflow.studio.model.region.Region;
import com.mangaflow.studio.model.task.Task;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", uses = {TaskSubmissionMapper.class, TaskAttachmentMapper.class})
public interface TaskMapper {

    @Mapping(target = "regions", expression = "java(toRegionBasicList(task.getRegions()))")
    @Mapping(target = "assistant", expression = "java(toBasicUser(task.getAssistant()))")
    @Mapping(target = "assignedBy", expression = "java(toBasicUser(task.getAssignedBy()))")
    TaskResponse toResponse(Task task);

    default List<RegionBasicDTO> toRegionBasicList(Set<Region> regions) {
        if (regions == null) return Collections.emptyList();
        return regions.stream()
                .map(this::toBasicRegion)
                .toList();
    }

    default RegionBasicDTO toBasicRegion(Region region) {
        if (region == null) return null;
        return RegionBasicDTO.builder()
                .id(region.getId())
                .regionType(region.getRegionType())
                .label(region.getLabel())
                .x(region.getX())
                .y(region.getY())
                .width(region.getWidth())
                .height(region.getHeight())
                .color(region.getColor())
                .build();
    }

    default UserBasicDTO toBasicUser(User user) {
        if (user == null) return null;
        return UserBasicDTO.builder()
                .id(user.getId())
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }
}
