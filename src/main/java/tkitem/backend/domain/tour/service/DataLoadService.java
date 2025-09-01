package tkitem.backend.domain.tour.service;

import java.io.FileNotFoundException;

public interface DataLoadService {

    /**
     * 지정된 경로의 CSV 파일을 읽어 Tour 관련 데이터를 데이터베이스에 적재합니다.
     * @param filePath CSV 파일의 전체 경로
     * @throws FileNotFoundException 파일이 존재하지 않을 경우 발생
     */
    void loadDataFromCsv(String filePath) throws Exception;
}