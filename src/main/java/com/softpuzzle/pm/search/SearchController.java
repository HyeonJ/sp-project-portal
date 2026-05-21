package com.softpuzzle.pm.search;

import com.softpuzzle.pm.common.ApiResponse;
import com.softpuzzle.pm.common.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchController {

    private final SearchService searchService;
    private final CurrentUser currentUser;

    public SearchController(SearchService searchService, CurrentUser currentUser) {
        this.searchService = searchService;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/search")
    public ApiResponse<SearchService.SearchResults> search(@RequestParam(name = "q", required = false) String q) {
        return ApiResponse.ok(searchService.search(q, currentUser.require()));
    }
}
