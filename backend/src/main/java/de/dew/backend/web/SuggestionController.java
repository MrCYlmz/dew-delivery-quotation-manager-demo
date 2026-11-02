package de.dew.backend.web;

import de.dew.backend.error.ValidationException;
import de.dew.backend.repo.SuggestionRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/suggestions")
public class SuggestionController {

    private final SuggestionRepository suggestions;

    public SuggestionController(SuggestionRepository suggestions) {
        this.suggestions = suggestions;
    }

    @GetMapping("/companies")
    public List<String> companies() {
        return suggestions.companies();
    }

    @GetMapping("/countries")
    public List<String> countries(@RequestParam("field") String field) {
        return guard(() -> suggestions.countries(field));
    }

    @GetMapping("/cities")
    public List<String> cities(@RequestParam("field") String field,
            @RequestParam(name = "country", required = false) String country) {
        return guard(() -> suggestions.cities(field, country));
    }

    private static List<String> guard(java.util.function.Supplier<List<String>> query) {
        try {
            return query.get();
        } catch (IllegalArgumentException e) {
            throw new ValidationException("field",
                    "must be one of " + SuggestionRepository.allowedFields());
        }
    }
}
