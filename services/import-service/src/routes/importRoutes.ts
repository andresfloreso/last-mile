import { Router, Response } from "express";
import multer from "multer";
import path from "path";
import { AuthenticatedRequest, jwtAuth, requireRole } from "../middleware/jwtAuth";
import { parseShipmentsExcel } from "../services/excelParser";
import { parseShipmentsCsv } from "../services/csvParser";
import { importShipmentRows } from "../services/importOrchestrator";
import { DeliveryServiceClient } from "../services/deliveryServiceClient";
import { TEMPLATE_FILENAME, buildImportTemplate } from "../services/template";

const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 15 * 1024 * 1024 }, // 15 MB
  fileFilter: (_req, file, cb) => {
    const ext = path.extname(file.originalname).toLowerCase();
    if ([".xlsx", ".xls", ".csv"].includes(ext)) {
      cb(null, true);
    } else {
      cb(new Error("Formato no soportado. Usa .xlsx, .xls o .csv"));
    }
  },
});

const router = Router();

// Plantilla .xlsx con las columnas esperadas, incluida la modulación (HU-25).
router.get(
  "/template",
  jwtAuth,
  requireRole("ADMIN", "DISPATCHER", "OPERATOR"),
  async (_req: AuthenticatedRequest, res: Response) => {
    const buffer = await buildImportTemplate();
    res
      .status(200)
      .set({
        "Content-Type": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "Content-Disposition": `attachment; filename="${TEMPLATE_FILENAME}"`,
      })
      .send(buffer);
  }
);

// Sólo roles operativos pueden cargar planillas masivas de envíos.
router.post(
  "/shipments",
  jwtAuth,
  requireRole("ADMIN", "DISPATCHER", "OPERATOR"),
  upload.single("file"),
  async (req: AuthenticatedRequest, res: Response) => {
    if (!req.file) {
      res.status(400).json({ error: "Debes adjuntar un archivo en el campo 'file'" });
      return;
    }

    const bearerToken = (req.headers.authorization as string).substring(7);
    const ext = path.extname(req.file.originalname).toLowerCase();

    let parseResult;
    try {
      parseResult =
        ext === ".csv"
          ? parseShipmentsCsv(req.file.buffer.toString("utf-8"))
          : await parseShipmentsExcel(req.file.buffer);
    } catch (err) {
      console.error("No se pudo leer la planilla", err);
      res.status(422).json({ error: "No se pudo leer el archivo. Verifica que sea un .xlsx o .csv válido." });
      return;
    }

    // Problema del archivo completo (sin hojas o faltan columnas): no hay filas que resumir.
    const fileError = parseResult.errors.find((e) => e.rowNumber <= 1);
    if (parseResult.rows.length === 0 && fileError) {
      res.status(422).json({ error: fileError.message, details: parseResult.errors });
      return;
    }

    try {
      const summary = await importShipmentRows(
        parseResult.rows,
        parseResult.errors,
        new DeliveryServiceClient(bearerToken),
        `import:${req.file.originalname}`
      );
      // 200 aunque algunas filas fallen: el resumen informa creados, omitidos y errores por fila.
      res.status(200).json(summary);
    } catch (err) {
      console.error("Error procesando la importación", err);
      res.status(500).json({ error: "Error procesando la importación" });
    }
  }
);

export default router;
