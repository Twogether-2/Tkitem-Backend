package tkitem.backend.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.DeleteObjectRequest;

import tkitem.backend.domain.image.ImageServiceImpl;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {
    private static final String BUCKET = "test-bucket";
    private static final String REGION = "ap-northeast-2";

    @Mock
    AmazonS3 amazonS3;

    ImageServiceImpl imageService;

    @BeforeEach
    void setUp() {
        imageService = new ImageServiceImpl(amazonS3);
        // Inject @Value fields on the service under test
        ReflectionTestUtils.setField(imageService, "bucket", BUCKET);
        ReflectionTestUtils.setField(imageService, "region", REGION);
    }

    @Test
    @DisplayName("단일 파일 업로드 성공: putObject 호출 및 URL 반환")
    void upload_success() throws Exception {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "hello.png",
                "image/png",
                "PNGDATA".getBytes(StandardCharsets.UTF_8)
        );

        when(amazonS3.getUrl(eq(BUCKET), any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(1, String.class);
            return new URL("https://" + BUCKET + ".s3." + REGION + ".amazonaws.com/" + key);
        });

        // when
        String url = imageService.upload(file);

        // then
        assertThat(url).contains("https://" + BUCKET + ".s3." + REGION + ".amazonaws.com/");
        assertThat(url).endsWith("hello.png"); // UUID-hello.png 형태

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(amazonS3, times(1)).putObject(eq(BUCKET), keyCaptor.capture(), any(), any());
        assertThat(keyCaptor.getValue()).endsWith("hello.png");
    }

    @Test
    @DisplayName("다중 파일 업로드 성공: 각각 URL 반환")
    void uploadMultiple_success() throws Exception {
        // given
        MockMultipartFile f1 = new MockMultipartFile("file1", "a.jpg", "image/jpeg", "A".getBytes());
        MockMultipartFile f2 = new MockMultipartFile("file2", "b.jpg", "image/jpeg", "B".getBytes());

        when(amazonS3.getUrl(eq(BUCKET), any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(1, String.class);
            return new URL("https://" + BUCKET + ".s3." + REGION + ".amazonaws.com/" + key);
        });

        // when
        List<String> urls = imageService.uploadMultiple(List.of(f1, f2));

        // then
        assertThat(urls).hasSize(2);
        assertThat(urls.get(0)).endsWith("a.jpg");
        assertThat(urls.get(1)).endsWith("b.jpg");

        verify(amazonS3, times(2)).putObject(eq(BUCKET), any(), any(), any());
    }

    @Test
    @DisplayName("업로드 중 S3 예외 발생 시 래핑 예외 전파")
    void upload_s3Exception() throws Exception {
        // given
        MockMultipartFile file = new MockMultipartFile("file", "x.txt", "text/plain", "X".getBytes());

        doThrow(new AmazonS3Exception("boom")).when(amazonS3)
                .putObject(eq(BUCKET), any(), any(), any());

        // when / then
        assertThatThrownBy(() -> imageService.upload(file))
                .isInstanceOf(AmazonS3Exception.class)
                .hasMessageContaining("Failed to upload multiple files");
    }

    @Test
    @DisplayName("이미지 URL로 삭제: path-style URL 파싱하여 key로 deleteObject 호출")
    void delete_success_pathStyle() {
        // given
        String key = "folder/img-1.png";
        String imgUrl = "https://s3." + REGION + ".amazonaws.com/" + BUCKET + "/" + key;

        // when
        imageService.delete(imgUrl);

        // then
        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(amazonS3, times(1)).deleteObject(captor.capture());
        DeleteObjectRequest req = captor.getValue();
        assertThat(req.getBucketName()).isEqualTo(BUCKET);
        assertThat(req.getKey()).isEqualTo(key);
    }

    @Test
    @DisplayName("삭제 중 S3 예외 발생 시 래핑 예외 전파")
    void delete_s3Exception() {
        // given
        String key = "img.png";
        String imgUrl = "https://s3." + REGION + ".amazonaws.com/" + BUCKET + "/" + key;

        doThrow(new AmazonS3Exception("del-fail"))
                .when(amazonS3).deleteObject(any(DeleteObjectRequest.class));

        // when / then
        assertThatThrownBy(() -> imageService.delete(imgUrl))
                .isInstanceOf(AmazonS3Exception.class)
                .hasMessageContaining("Failed to delete multiple files");
    }
}
