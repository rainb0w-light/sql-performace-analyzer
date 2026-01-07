import html2pdf from 'html2pdf.js'

export async function exportToPdf(element, filename) {
  try {
    // 确保所有 details 元素都展开
    const detailsElements = element.querySelectorAll('details')
    detailsElements.forEach(detail => {
      detail.open = true
    })

    const opt = {
      margin: 1,
      filename: filename || `sql-analysis-report-${new Date().getTime()}.pdf`,
      image: { type: 'jpeg', quality: 0.98 },
      html2canvas: { scale: 2 },
      jsPDF: { unit: 'in', format: 'a4', orientation: 'portrait' }
    }

    await html2pdf().set(opt).from(element).save()
  } catch (err) {
    throw new Error('PDF 导出失败: ' + err.message)
  }
}








