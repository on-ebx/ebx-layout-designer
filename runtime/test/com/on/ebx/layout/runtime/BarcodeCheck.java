package com.on.ebx.layout.runtime;

/** Checks the GS1 check digit and the EAN bar patterns (run by tools/package.sh). */
public final class BarcodeCheck
{
	public static void main(final String[] args)
	{
		BarcodeCheck.expect(Barcode.checkDigitOk("4006381333931"), "EAN-13 valid");
		BarcodeCheck.expect(!Barcode.checkDigitOk("4006381333932"), "EAN-13 wrong digit");
		BarcodeCheck.expect(Barcode.checkDigitOk("96385074"), "EAN-8 valid");
		BarcodeCheck.expect(Barcode.checkDigitOk("036000291452"), "UPC-A valid");
		BarcodeCheck.expect(Barcode.checkDigitOk("10614141000415"), "GTIN-14 valid");
		BarcodeCheck.expect(Barcode.bits("4006381333931").length() == 95, "EAN-13 has 95 modules");
		BarcodeCheck.expect(Barcode.bits("96385074").length() == 67, "EAN-8 has 67 modules");
		BarcodeCheck.expect(Barcode.bits("036000291452").length() == 95, "UPC-A drawn as EAN-13");
		BarcodeCheck.expect(Barcode.bits("4006381333931").startsWith("101") && Barcode.bits("4006381333931").endsWith("101"), "guards");
		System.out.println("BarcodeCheck: all checks passed");
	}

	private static void expect(final boolean pOk, final String pWhat)
	{
		if (!pOk)
		{
			throw new AssertionError(pWhat);
		}
	}
}
