package com.example.util.pdf.mapper

import android.graphics.Color
import com.example.data.model.Client
import com.example.data.model.Invoice
import com.example.data.model.InvoiceItem
import com.example.data.model.StoreSettings
import com.example.util.FormatUtils
import com.example.util.pdf.model.*
import java.text.SimpleDateFormat
import java.util.*

object InvoicePdfMapper {

    fun mapToPdfModel(
        invoice: Invoice,
        items: List<InvoiceItem>,
        client: Client?,
        settings: StoreSettings
    ): PdfReportModel {
        val currency = invoice.currency.ifBlank { settings.currency.ifBlank { "الريال اليمني" } }
        val dateFmt = SimpleDateFormat("yyyy/MM/dd | hh:mm a", Locale("ar"))
        val formattedDate = dateFmt.format(Date(invoice.date))

        val storeName = settings.storeName.ifBlank { "الحكيمي للأدوية والمستلزمات الطبية" }

        val metaFields = mutableListOf(
            PdfHeaderMeta("رقم الفاتورة", "#${invoice.invoiceNumber}"),
            PdfHeaderMeta("التاريخ والوقت", formattedDate),
            PdfHeaderMeta("اسم العميل", invoice.clientName.ifBlank { "عميل نقدي" }),
            PdfHeaderMeta("نوع الفاتورة", if (invoice.isQuickInvoice) "فاتورة سريعة" else "فاتورة تفصيلية"),
            PdfHeaderMeta("العملة المعتمدة", currency)
        )

        if (client != null && client.phone.isNotBlank()) {
            metaFields.add(PdfHeaderMeta("رقم هاتف العميل", client.phone))
        }

        val headerInfo = PdfHeaderInfo(
            storeName = storeName,
            storePhone = settings.storePhone,
            storeAddress = settings.storeAddress,
            reportTitle = "🧾 فاتورة مبيعات رسمية",
            reportSubtitle = "فاتورة مبيعات رقم #${invoice.invoiceNumber}",
            metaFields = metaFields
        )

        val columns = listOf(
            PdfTableColumn("#", "#", widthWeight = 0.5f, alignment = PdfAlignment.CENTER),
            PdfTableColumn("name", "اسم الصنف / البيان", widthWeight = 3.5f, alignment = PdfAlignment.RIGHT),
            PdfTableColumn("qty", "الكمية", widthWeight = 1.0f, alignment = PdfAlignment.CENTER),
            PdfTableColumn("price", "السعر ($currency)", widthWeight = 1.5f, alignment = PdfAlignment.LEFT),
            PdfTableColumn("discount", "الخصم ($currency)", widthWeight = 1.2f, alignment = PdfAlignment.LEFT),
            PdfTableColumn("total", "الإجمالي ($currency)", widthWeight = 1.8f, alignment = PdfAlignment.LEFT, isBold = true)
        )

        val rows = mutableListOf<PdfTableRow>()

        if (invoice.isQuickInvoice) {
            val desc = invoice.description?.ifBlank { "مبيعات عامة سريعة" } ?: "مبيعات عامة سريعة"
            rows.add(
                PdfTableRow(
                    cells = listOf(
                        PdfCell("1", alignment = PdfAlignment.CENTER),
                        PdfCell(desc, alignment = PdfAlignment.RIGHT, isBold = true),
                        PdfCell("1", alignment = PdfAlignment.CENTER),
                        PdfCell(FormatUtils.formatAmount(invoice.totalAmount), alignment = PdfAlignment.LEFT),
                        PdfCell("0", alignment = PdfAlignment.LEFT),
                        PdfCell(FormatUtils.formatAmount(invoice.totalAmount), alignment = PdfAlignment.LEFT, isBold = true)
                    )
                )
            )
        } else {
            items.forEachIndexed { index, item ->
                val lineDiscount = (item.unitPrice * item.quantity) - item.totalPrice
                val discountVal = if (lineDiscount > 0.001) lineDiscount else 0.0
                rows.add(
                    PdfTableRow(
                        cells = listOf(
                            PdfCell((index + 1).toString(), alignment = PdfAlignment.CENTER),
                            PdfCell(item.itemName, alignment = PdfAlignment.RIGHT),
                            PdfCell(item.quantity.toString(), alignment = PdfAlignment.CENTER),
                            PdfCell(FormatUtils.formatAmount(item.unitPrice), alignment = PdfAlignment.LEFT),
                            PdfCell(FormatUtils.formatAmount(discountVal), alignment = PdfAlignment.LEFT),
                            PdfCell(FormatUtils.formatAmount(item.totalPrice), alignment = PdfAlignment.LEFT, isBold = true)
                        )
                    )
                )
            }
        }

        val subtotal = if (invoice.isQuickInvoice) invoice.totalAmount else items.sumOf { it.totalPrice }

        val summaryItems = mutableListOf(
            PdfSummaryItem("المجموع الفرعي", "${FormatUtils.formatAmount(subtotal)} $currency"),
            PdfSummaryItem("الخصم الإضافي", "${FormatUtils.formatAmount(invoice.discount)} $currency"),
            PdfSummaryItem("الإجمالي الكلي النهائي", "${FormatUtils.formatAmount(invoice.totalAmount)} $currency", isHighlighted = true, highlightColor = Color.parseColor("#E0F2FE")),
            PdfSummaryItem("المبلغ المدفوع (نقداً/سند)", "${FormatUtils.formatAmount(invoice.paidAmount)} $currency"),
            PdfSummaryItem("المبلغ المتبقي الآجل", "${FormatUtils.formatAmount(invoice.remainingAmount)} $currency", isHighlighted = invoice.remainingAmount > 0, highlightColor = if (invoice.remainingAmount > 0) Color.parseColor("#FEF2F2") else null)
        )

        if (client != null) {
            val clientBal = client.balance
            val clientCurr = client.currency.ifBlank { currency }
            val balText = if (clientBal > 0) {
                "${FormatUtils.formatAmount(clientBal)} $clientCurr (مدين/مستحق علي العميل)"
            } else if (clientBal < 0) {
                "${FormatUtils.formatAmount(-clientBal)} $clientCurr (دائن/لصالح العميل)"
            } else {
                "0 $clientCurr (الحساب خالص)"
            }
            summaryItems.add(PdfSummaryItem("إجمالي رصيد العميل الحالي", balText, isHighlighted = clientBal > 0, highlightColor = if (clientBal > 0) Color.parseColor("#FEE2E2") else null))
        }

        val table = PdfTable(
            title = "جدول تفاصيل الفاتورة",
            columns = columns,
            rows = rows
        )

        return PdfReportModel(
            title = "فاتورة رقم #${invoice.invoiceNumber}",
            headerInfo = headerInfo,
            tables = listOf(table),
            summaryInfo = PdfSummaryInfo(title = "الملخص المالي للفاتورة", items = summaryItems),
            notes = invoice.notes,
            orientation = PdfOrientation.PORTRAIT,
            currency = currency
        )
    }
}
