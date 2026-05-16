package org.agty.mtx.controller;

import org.agty.mtx.dto.GroupDto;
import org.agty.mtx.dto.GroupRequest;
import org.agty.mtx.dto.ReorderRequest;
import org.agty.mtx.entity.UserAccount;
import org.agty.mtx.service.ChatAppService;
import org.agty.mtx.service.CurrentUserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final ChatAppService chatAppService;
    private final CurrentUserService currentUserService;

    public GroupController(ChatAppService chatAppService, CurrentUserService currentUserService) {
        this.chatAppService = chatAppService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<GroupDto> listGroups() {
        UserAccount user = currentUserService.requireCurrentUser();
        return chatAppService.listGroups(user);
    }

    @PostMapping
    public GroupDto createGroup(@RequestBody GroupRequest request) {
        UserAccount user = currentUserService.requireCurrentUser();
        return chatAppService.createGroup(user, request.getName());
    }

    @PutMapping("/{groupId}")
    public GroupDto updateGroup(@PathVariable Long groupId, @RequestBody GroupRequest request) {
        UserAccount user = currentUserService.requireCurrentUser();
        return chatAppService.updateGroup(user, groupId, request.getName());
    }

    @DeleteMapping("/{groupId}")
    public void deleteGroup(@PathVariable Long groupId) {
        UserAccount user = currentUserService.requireCurrentUser();
        chatAppService.deleteGroup(user, groupId);
    }

    @PatchMapping("/reorder")
    public void reorderGroups(@RequestBody ReorderRequest request) {
        UserAccount user = currentUserService.requireCurrentUser();
        chatAppService.reorderGroups(user, request.getIds());
    }
}
