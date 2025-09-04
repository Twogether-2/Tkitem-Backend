package tkitem.backend.domain.checklist.service;

import org.springframework.stereotype.Component;
import tkitem.backend.domain.checklist.dto.ChecklistItemRow;
import tkitem.backend.domain.checklist.dto.TripMeta;
import tkitem.backend.domain.checklist.dto.TripPlace;
import tkitem.backend.domain.checklist.vo.ReasonContext;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class ReasonContextBuilder {

    private static final Pattern FLAGS = Pattern.compile("FLAGS:([A-Z,]+)");
    private static final Pattern HIST  = Pattern.compile("HIST:([^|]+)");
    private static final Pattern ACT   = Pattern.compile("ACT:([^|]+)");

    public ReasonContext build(Long tripId,
                               List<ChecklistItemRow> items,
                               TripMeta meta,
                               List<TripPlace> places) {

        // === 메타 ===
        int dayCount = Optional.ofNullable(meta)
                .map(TripMeta::getTripDays)
                .orElseGet(() -> items.stream()
                        .map(ChecklistItemRow::getScheduleDate)
                        .filter(Objects::nonNull)
                        .max(Comparator.naturalOrder())
                        .orElse(0));

        int nightCount = Math.max(dayCount - 1, 1);

        List<String> cities = places == null ? List.of() :
                places.stream().map(TripPlace::getCityName)
                        .filter(Objects::nonNull).distinct().toList();

        List<String> countries = places == null ? List.of() :
                places.stream().map(TripPlace::getCountryName)
                        .filter(Objects::nonNull).distinct().toList();

        String headlinePlace = resolveHeadlinePlace(countries, cities);

        // === 전역 수집 ===
        Set<String> flags = new LinkedHashSet<>();
        List<String> hist = new ArrayList<>();
        Set<String> acts  = new LinkedHashSet<>();
        List<String> raw  = new ArrayList<>();

        // === 아이템별 증거 ===
        List<ReasonContext.ItemEvidence> evidences = new ArrayList<>();

        for (var it : items) {
            String notes = Optional.ofNullable(it.getNotes()).orElse("");
            raw.add(notes);

            // FLAGS
            var mf = FLAGS.matcher(notes);
            List<String> itemFlags = new ArrayList<>();
            if (mf.find()) {
                for (String t : mf.group(1).split(",")) {
                    String f = t.trim();
                    if (!f.isBlank()) {
                        flags.add(f);
                        itemFlags.add(f);
                    }
                }
            }

            // HIST
            var mh = HIST.matcher(notes);
            if (mh.find()) {
                String h = mh.group(1).trim();
                if (!h.isBlank()) hist.add(h);
            }

            // ACT
            var ma = ACT.matcher(notes);
            if (ma.find()) {
                for (String t : ma.group(1).split(",")) {
                    String a = t.trim().toUpperCase();
                    if (!a.isBlank()) acts.add(a);
                }
            }

            // reasonSnippets = " | " split 후 ACT/FLAGS/DURATION 토큰 제거
            List<String> reasonSnips = Arrays.stream(notes.split("\\s*\\|\\s*"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .filter(s -> !s.startsWith("ACT:"))
                    .filter(s -> !s.startsWith("FLAGS:"))
                    .filter(s -> !s.startsWith("DURATION:"))
                    .distinct()
                    .toList();

            evidences.add(new ReasonContext.ItemEvidence(
                    it.getItemName(),
                    it.getScheduleDate(),
                    it.getTier(),
                    it.getScore(),
                    itemFlags,
                    reasonSnips
            ));
        }

        // reasons = raw notes에서 토막 추출(중복 제거)
        List<String> reasons = raw.stream()
                .flatMap(n -> Arrays.stream(n.split("\\s*\\|\\s*")))
                .map(s -> s.replaceFirst("^ACT:.*$", ""))
                .map(s -> s.replaceFirst("^FLAGS:.*$", ""))
                .map(s -> s.replaceFirst("^DURATION:.*$", ""))
                .map(String::trim).filter(s -> !s.isBlank())
                .distinct().limit(24).toList();

        Integer monthNum = Optional.ofNullable(meta).map(TripMeta::getMonthNum).orElse(null);

        return new ReasonContext(
                tripId,
                monthNum,
                dayCount,
                nightCount,
                cities,
                countries,
                headlinePlace,
                new ArrayList<>(flags),
                new ArrayList<>(acts),
                reasons,
                hist,
                evidences
        );
    }

    private String resolveHeadlinePlace(List<String> countries, List<String> cities) {
        if (countries != null && countries.size() == 1) return countries.get(0);
        if (cities != null && !cities.isEmpty()) {
            if (cities.size() == 1) return cities.get(0);
            // 도시 2개까지만 합쳐 간결하게
            return String.join("·", cities.subList(0, Math.min(2, cities.size())));
        }
        return "해외";
    }
}



