package com.tvpc.domain;

/**
 * Export result containing Excel file data.
 */
public class ExportResult {
    private final byte[] excelData;
    private final String filename;
    private final int recordCount;

    public ExportResult(byte[] excelData, String filename, int recordCount) {
        this.excelData = excelData;
        this.filename = filename;
        this.recordCount = recordCount;
    }

    public byte[] getExcelData() { return excelData; }
    public String getFilename() { return filename; }
    public int getRecordCount() { return recordCount; }

    @Override
    public String toString() {
        return "ExportResult{" +
                "filename='" + filename + '\'' +
                ", recordCount=" + recordCount +
                ", dataSize=" + (excelData != null ? excelData.length : 0) +
                '}';
    }
}
