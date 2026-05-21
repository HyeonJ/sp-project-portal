package com.softpuzzle.pm.deliverable;

import com.softpuzzle.pm.common.ApiResponse;
import com.softpuzzle.pm.common.CurrentUser;
import com.softpuzzle.pm.deliverable.dto.CommentRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class CommentController {

    private final CommentService commentService;
    private final CurrentUser currentUser;

    public CommentController(CommentService commentService, CurrentUser currentUser) {
        this.commentService = commentService;
        this.currentUser = currentUser;
    }

    @GetMapping("/slots/{slotType}/comments")
    public ApiResponse<List<Comment>> list(@PathVariable Long projectId, @PathVariable String slotType) {
        return ApiResponse.ok(commentService.list(projectId, slotType, currentUser.require()));
    }

    @PostMapping("/slots/{slotType}/comments")
    public ApiResponse<Comment> add(@PathVariable Long projectId, @PathVariable String slotType,
                                    @Valid @RequestBody CommentRequest req) {
        return ApiResponse.ok(commentService.add(projectId, slotType, req.body(), currentUser.require()));
    }

    @PatchMapping("/comments/{commentId}")
    public ApiResponse<Void> edit(@PathVariable Long projectId, @PathVariable Long commentId,
                                  @Valid @RequestBody CommentRequest req) {
        commentService.edit(projectId, commentId, req.body(), currentUser.require());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/comments/{commentId}")
    public ApiResponse<Void> delete(@PathVariable Long projectId, @PathVariable Long commentId) {
        commentService.delete(projectId, commentId, currentUser.require());
        return ApiResponse.ok(null);
    }
}
