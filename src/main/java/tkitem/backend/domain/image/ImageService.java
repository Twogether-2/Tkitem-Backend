package tkitem.backend.domain.image;

import java.io.IOException;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

public interface ImageService {
	String upload(MultipartFile file) throws IOException;
	List<String> uploadMultiple(List<MultipartFile> files) throws IOException;
	void delete(String imgUrl);
}
