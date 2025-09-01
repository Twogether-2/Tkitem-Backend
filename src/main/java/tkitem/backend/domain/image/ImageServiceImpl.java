package tkitem.backend.domain.image;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageServiceImpl implements ImageService {

	@Value("${cloud.aws.s3.bucket}")
	private String bucket;

	@Value("${cloud.aws.cdn.url}")
	private String cdnUrl; // Optional: if empty, fallback to S3 URL

	private final AmazonS3 amazonS3;

	@Override
	public String upload(MultipartFile multipartFile) throws IOException {
		try {
			// 파일 이름의 중복을 막기 위해 "UUID(랜덤 값) + 원본파일이름"로 연결함
			String s3FileName = UUID.randomUUID() + "-" + multipartFile.getOriginalFilename();

			ObjectMetadata objMeta = new ObjectMetadata();
			// url을 클릭 시 사진이 웹에서 보이는 것이 아닌 바로 다운되는 현상을 해결하기 위해 메타데이터 타입 설정
			objMeta.setContentType(multipartFile.getContentType());
			// 정확한 Content-Length 설정 (available() 대신 getSize())
			objMeta.setContentLength(multipartFile.getSize());

			// 파일 stream을 열어서 S3에 파일을 업로드 (자동 자원 해제)
			try (InputStream inputStream = multipartFile.getInputStream()) {
				amazonS3.putObject(bucket, s3FileName, inputStream, objMeta);
			}
			
			return amazonS3.getUrl(bucket, s3FileName).toString();
		}
		catch (AmazonS3Exception e) {
			throw new AmazonS3Exception("Failed to upload file", e);
		}
	}

	@Override
	public List<String> uploadMultiple(List<MultipartFile> multipartFileList) throws IOException {
		List<String> fileList = new ArrayList<>();
		try {
			for (MultipartFile file : multipartFileList) {
				// 파일 이름의 중복을 막기 위해 "UUID(랜덤 값) + 원본파일이름"로 연결함
				String s3FileName = UUID.randomUUID() + "-" + file.getOriginalFilename();

				ObjectMetadata objMeta = new ObjectMetadata();
				// url을 클릭 시 사진이 웹에서 보이는 것이 아닌 바로 다운되는 현상을 해결하기 위해 메타데이터 타입 설정
				objMeta.setContentType(file.getContentType());
				// 정확한 Content-Length 설정
				objMeta.setContentLength(file.getSize());

				// 파일 stream을 열어서 S3에 파일을 업로드 (자동 자원 해제)
				try (InputStream inputStream = file.getInputStream()) {
					amazonS3.putObject(bucket, s3FileName, inputStream, objMeta);
				}

				// Url 가져와서 반환
				log.info("S3 upload file name = {}", s3FileName);
				if (cdnUrl != null && !cdnUrl.isBlank()) {
					String normalizedCdn = cdnUrl.endsWith("/") ? cdnUrl.substring(0, cdnUrl.length() - 1) : cdnUrl;
					fileList.add(normalizedCdn + "/" + s3FileName);
				} else {
					fileList.add(amazonS3.getUrl(bucket, s3FileName).toString());
				}
			}
		}
		catch (AmazonS3Exception e) {
			throw new AmazonS3Exception("Failed to upload multiple files", e);
		}
		return fileList;
	}

	private String extractKeyFromUrl(String imgUrl) {
		if (imgUrl == null || imgUrl.isBlank()) return null;
		// 1) CDN이 설정되어 있고 URL이 CDN으로 시작하면 해당 부분을 제거
		if (cdnUrl != null && !cdnUrl.isBlank()) {
			String normalizedCdn = cdnUrl.endsWith("/") ? cdnUrl.substring(0, cdnUrl.length() - 1) : cdnUrl;
			if (imgUrl.startsWith(normalizedCdn + "/")) {
				return imgUrl.substring((normalizedCdn + "/").length());
			}
		}
		// 2) 일반적인 Amazon S3 URL 파싱
		int idx = imgUrl.indexOf(".amazonaws.com/");
		if (idx > -1) {
			String afterHost = imgUrl.substring(idx + ".amazonaws.com/".length());
			// 경로 스타일: s3.<region>.amazonaws.com/<bucket>/<key>
			// 가상 호스트 스타일: <bucket>.s3.<region>.amazonaws.com/<key>
			if (afterHost.startsWith(bucket + "/")) {
				return afterHost.substring((bucket + "/").length());
			}
			return afterHost; // already the key in virtual-hosted style
		}
		// 3) 대체 방법: 마지막으로 등장한 버킷 이름 뒤의 문자열을 추출
		String marker = "/" + bucket + "/";
		int midx = imgUrl.indexOf(marker);
		if (midx > -1) {
			return imgUrl.substring(midx + marker.length());
		}
		// 4) 최후의 수단: 마지막 '/' 뒤의 문자열을 추출
		int lastSlash = imgUrl.lastIndexOf('/');
		return (lastSlash > -1 && lastSlash + 1 < imgUrl.length()) ? imgUrl.substring(lastSlash + 1) : imgUrl;
	}

	@Override
	public void delete(String imgUrl) {
		try {
			String key = extractKeyFromUrl(imgUrl);
			log.info("Deleting S3 object. bucket={}, key={}, originalUrl={}", bucket, key, imgUrl);
			DeleteObjectRequest request = new DeleteObjectRequest(bucket, key);
			amazonS3.deleteObject(request);
		}
		catch (AmazonS3Exception e) {
			throw new AmazonS3Exception("Failed to delete file", e);
		}
	}
}
