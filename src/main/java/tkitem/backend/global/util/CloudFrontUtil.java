package tkitem.backend.global.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CloudFrontUtil {

    public static String cdnUrl;
    public static String bucket;

    public CloudFrontUtil(
            @Value("${cloud.aws.cdn.url}") String cdnUrl,
            @Value("${cloud.aws.s3.bucket}") String bucket) {
        this.cdnUrl = cdnUrl;
        this.bucket = bucket;
    }

    /**
     * S3 URL을 CloudFront CDN URL로 변환
     * @param imgUrl S3 URL (예: https://bucket-name.s3.ap-northeast-2.amazonaws.com/path/to/image.jpg)
     * @return CloudFront CDN URL (예: https://cdn-domain.cloudfront.net/path/to/image.jpg)
     */
    public static String getCloudFrontUrl(String imgUrl) {
        if (imgUrl == null || imgUrl.trim().isEmpty()) {
            return imgUrl;
        }

        // 이미 CDN URL인 경우 그대로 반환
        if (imgUrl.contains("cloudfront.net") || (cdnUrl != null && imgUrl.contains(cdnUrl))) {
            return imgUrl;
        }

        // S3 URL에서 경로 부분 추출
        String path = extractPathFromS3Url(imgUrl);
        if (path == null) {
            return imgUrl;
        }

        // CloudFront URL 생성 (중복 슬래시 방지)
        if (cdnUrl == null || cdnUrl.isBlank()) {
            return imgUrl;
        }
        String base = cdnUrl.startsWith("http") ? cdnUrl : ("https://" + cdnUrl);
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/" + path;
    }

    /**
     * S3 URL에서 경로 부분만 추출
     * 지원 패턴:
     * - 가상 호스팅 스타일 (리전): https://<bucket>.s3.<region>.amazonaws.com/<key>
     * - 가상 호스팅 스타일 (글로벌): https://<bucket>.s3.amazonaws.com/<key>
     * - 경로 스타일 (리전): https://s3.<region>.amazonaws.com/<bucket>/<key>
     * - 경로 스타일 (글로벌): https://s3.amazonaws.com/<bucket>/<key>
     * - 일부 리전의 하이픈 표기: s3-<region>.amazonaws.com
     */
    public static String extractPathFromS3Url(String s3Url) {
        try {
            if (s3Url == null || s3Url.isBlank()) return null;

            // 쿼리스트링/프래그먼트 제거
            String clean = s3Url.split("[?#]", 2)[0];

            // 1) 가상 호스팅 스타일 (리전): https://bucket.s3.ap-northeast-2.amazonaws.com/key
            java.util.regex.Pattern vhostRegional = java.util.regex.Pattern.compile("^https?://([^.]+)\\.s3[.-]([a-z0-9-]+)\\.amazonaws\\.com/(.+)$");
            java.util.regex.Matcher m = vhostRegional.matcher(clean);
            if (m.find()) {
                String bucketInUrl = m.group(1);
                String key = m.group(3);
                // 내 버킷만 허용하고 싶다면 체크 (선택)
                if (bucket != null && !bucket.isBlank() && !bucket.equals(bucketInUrl)) {
                    log.debug("Bucket mismatch. expected={}, url={}", bucket, bucketInUrl);
                }
                return key;
            }

            // 2) 가상 호스팅 스타일 (글로벌): https://bucket.s3.amazonaws.com/key
            java.util.regex.Pattern vhostGlobal = java.util.regex.Pattern.compile("^https?://([^.]+)\\.s3\\.amazonaws\\.com/(.+)$");
            m = vhostGlobal.matcher(clean);
            if (m.find()) {
                return m.group(2);
            }

            // 3) 경로 스타일 (리전): https://s3.ap-northeast-2.amazonaws.com/bucket/key
            java.util.regex.Pattern pathRegional = java.util.regex.Pattern.compile("^https?://s3[.-]([a-z0-9-]+)\\.amazonaws\\.com/([^/]+)/(.+)$");
            m = pathRegional.matcher(clean);
            if (m.find()) {
                return m.group(3);
            }

            // 4) 경로 스타일 (글로벌): https://s3.amazonaws.com/bucket/key
            java.util.regex.Pattern pathGlobal = java.util.regex.Pattern.compile("^https?://s3\\.amazonaws\\.com/([^/]+)/(.+)$");
            m = pathGlobal.matcher(clean);
            if (m.find()) {
                return m.group(2);
            }

            // 5) 리전 하이픈 스타일: https://bucket.s3-ap-northeast-2.amazonaws.com/key
            java.util.regex.Pattern vhostHyphen = java.util.regex.Pattern.compile("^https?://([^.]+)\\.s3-([a-z0-9-]+)\\.amazonaws\\.com/(.+)$");
            m = vhostHyphen.matcher(clean);
            if (m.find()) {
                return m.group(3);
            }

            return null;
        } catch (Exception e) {
            log.warn("Failed to parse S3 URL: {}", s3Url, e);
            return null;
        }
    }
}
