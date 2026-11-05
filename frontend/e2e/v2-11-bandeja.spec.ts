import { type Page } from '@playwright/test';
import { sembrarConversacionDerivada } from './base-datos';
import { expect, test } from './fixture';

async function ingresar(page: Page, correo: string) {
  await page.goto('/ingresar');
  await page.getByLabel('Correo', { exact: true }).fill(correo);
  await page.getByLabel('Contraseña', { exact: true }).evaluate((elemento, valor) => {
    (elemento as HTMLInputElement).value = valor;
    elemento.dispatchEvent(new Event('input', { bubbles: true }));
  }, process.env['BT_DEMO_PASSWORD']!);
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  await expect(page).not.toHaveURL(/\/ingresar(?:\?|$)/);
  await expect(page.locator('main [aria-busy="true"]')).toHaveCount(0);
  if (/\/n\/barberturno\//.test(new URL(page.url()).pathname)) return;
  // Con dos relaciones se elige la barbería; con dos papeles en ella se abre solo el diálogo de papel,
  // que tapa el botón: en ese caso elige quien llama.
  const botonEntrar = page.getByRole('button', { name: 'Entrar en BarberTurno', exact: true });
  const dialogoPapel = page.getByRole('dialog').filter({ hasText: '¿Cómo quiere entrar' });
  await expect(botonEntrar.or(dialogoPapel).first()).toBeVisible();
  if (await dialogoPapel.isVisible()) return;
  await botonEntrar.click();
  await expect(page).toHaveURL(/\/n\/barberturno\//);
}

test.describe('V2-11 · Bandeja de WhatsApp (CB-14)', () => {
  test('flujo completo de derivación, respuesta, devolución al asistente y exclusión de menú para barbero', async ({
    page,
  }) => {
    // 1. Sembrar por SQL conversación en modo HUMANO con mensaje reciente y aviso para encargado
    const datos = sembrarConversacionDerivada('barberturno', 'multirol@ejemplo.test');
    expect(datos.conversacionId).toBeGreaterThan(0);

    // 2. Iniciar sesión como encargado
    await ingresar(page, 'multirol@ejemplo.test');

    // 3. Abrir el panel de avisos desde la cabecera
    const botonAvisos = page.getByRole('button', { name: /^Avisos/i });
    await expect(botonAvisos).toBeVisible();
    await botonAvisos.click();

    // 4. Encontrar el aviso de derivación y pulsar "Abrir conversación"
    const dialogoAvisos = page.getByRole('dialog');
    await expect(dialogoAvisos).toContainText('Cliente WhatsApp E2E solicita atención');

    const enlaceAbrir = dialogoAvisos.getByRole('link', {
      name: 'Abrir conversación',
      exact: true,
    });
    await expect(enlaceAbrir).toBeVisible();
    await enlaceAbrir.click();

    // 5. La navegación lleva a la bandeja con la conversación seleccionada
    await expect(page).toHaveURL(new RegExp(`/n/barberturno/whatsapp/${datos.conversacionId}$`));
    await expect(page.locator('main')).toContainText('Cliente WhatsApp E2E');
    await expect(page.locator('main')).toContainText('Hola, necesito hablar con un asesor');

    // 6. El personal responde dentro de la ventana de 24 h
    const textarea = page.getByLabel('Respuesta', { exact: true });
    await expect(textarea).toBeEnabled();
    await textarea.fill('Hola, te atiende el equipo de BarberTurno. ¿En qué te ayudamos?');

    const botonEnviar = page.getByRole('button', { name: 'Enviar', exact: true });
    await expect(botonEnviar).toBeEnabled();
    await botonEnviar.click();

    // 7. El mensaje enviado aparece en el historial con estado Pendiente
    await expect(page.locator('.historial-mensajes')).toContainText(
      'Hola, te atiende el equipo de BarberTurno. ¿En qué te ayudamos?',
    );
    await expect(page.locator('.historial-mensajes')).toContainText('Pendiente');

    // 8. Devolver la conversación al asistente virtual
    const botonDevolver = page.getByRole('button', { name: 'Devolver al asistente', exact: true });
    await expect(botonDevolver).toBeVisible();
    await botonDevolver.click();

    // Confirmar la devolución en el diálogo
    const dialogoConfirmacion = page.getByRole('dialog', { name: 'Devolver al asistente' });
    await expect(dialogoConfirmacion).toBeVisible();
    await dialogoConfirmacion.getByRole('button', { name: 'Devolver', exact: true }).click();

    // 9. Vuelve a la lista y la conversación ya no se encuentra en modo HUMANO
    await expect(page).toHaveURL(/\/n\/barberturno\/whatsapp$/);
    await expect(page.locator('main')).not.toContainText('Cliente WhatsApp E2E'); // sin conversaciones la lista no existe: estado vacío

    // 10. Un PROFESIONAL / BARBERO no ve la entrada de menú de WhatsApp
    await page.context().clearCookies(); // otra cuenta: sin la sesión del encargado
    await ingresar(page, 'personal-cliente@ejemplo.test');
    const dialogoPapel = page.getByRole('dialog');
    if (await dialogoPapel.isVisible()) {
      await dialogoPapel.getByRole('radio', { name: /^Como personal/ }).check();
      await dialogoPapel.getByRole('button', { name: 'Entrar', exact: true }).click();
    }
    await expect(page).toHaveURL(/\/n\/barberturno\/agenda$/);

    const menuPrincipal = page.locator('nav[aria-label="Navegación principal"]');
    await expect(
      menuPrincipal.getByRole('link', { name: 'WhatsApp', exact: true }),
    ).toHaveCount(0);
  });
});
