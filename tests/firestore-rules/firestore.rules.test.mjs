import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, it } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  deleteDoc,
  doc,
  getDoc,
  serverTimestamp,
  setDoc,
  updateDoc,
  writeBatch,
} from 'firebase/firestore';

const PROJECT_ID = 'demo-suavez';
const HOUSE_ID = 'house_1';
const ADMIN_UID = 'uid_admin';
const MEMBER_UID = 'uid_member';
const OTHER_MEMBER_UID = 'uid_other_member';
const OUTSIDER_UID = 'uid_outsider';

let testEnv;

function dbFor(uid) {
  return uid ? testEnv.authenticatedContext(uid).firestore() : testEnv.unauthenticatedContext().firestore();
}

async function seedBaseHouse() {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'users', ADMIN_UID), {
      uid: ADMIN_UID,
      nome: 'Admin',
      email: 'admin@suavez.app',
      currentHouseId: HOUSE_ID,
    });
    await setDoc(doc(db, 'users', MEMBER_UID), {
      uid: MEMBER_UID,
      nome: 'Member',
      email: 'member@suavez.app',
      currentHouseId: HOUSE_ID,
    });
    await setDoc(doc(db, 'users', OTHER_MEMBER_UID), {
      uid: OTHER_MEMBER_UID,
      nome: 'Other',
      email: 'other@suavez.app',
      currentHouseId: HOUSE_ID,
    });
    await setDoc(doc(db, 'houses', HOUSE_ID), {
      id: HOUSE_ID,
      nome: 'República Teste',
      codigoConvite: 'SV-1234',
      criadaPorUid: ADMIN_UID,
      semanaAtual: '2026-W46',
    });
    await setDoc(doc(db, 'houses', HOUSE_ID, 'members', ADMIN_UID), {
      uid: ADMIN_UID,
      nome: 'Admin',
      papel: 'ADMIN',
      pontosPorMes: { '2026-11': 10 },
      preferences: {},
    });
    await setDoc(doc(db, 'houses', HOUSE_ID, 'members', MEMBER_UID), {
      uid: MEMBER_UID,
      nome: 'Member',
      papel: 'MEMBER',
      pontosPorMes: { '2026-11': 5 },
      preferences: {},
    });
    await setDoc(doc(db, 'houses', HOUSE_ID, 'members', OTHER_MEMBER_UID), {
      uid: OTHER_MEMBER_UID,
      nome: 'Other',
      papel: 'MEMBER',
      pontosPorMes: { '2026-11': 0 },
      preferences: {},
    });
    await setDoc(doc(db, 'houses', HOUSE_ID, 'categories', 'limpeza'), {
      id: 'limpeza',
      nome: 'Limpeza',
      padrao: true,
    });
    await setDoc(doc(db, 'houses', HOUSE_ID, 'tasks', 'task_1'), {
      id: 'task_1',
      titulo: 'Lavar banheiro',
      peso: 8,
      ativa: true,
      criadaPorUid: ADMIN_UID,
    });
  });
}

describe('Sua Vez firestore.rules', () => {
  before(async () => {
    const [host, portStr] = (process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8080').split(':');
    testEnv = await initializeTestEnvironment({
      projectId: PROJECT_ID,
      firestore: {
        rules: readFileSync('../../firestore.rules', 'utf8'),
        host,
        port: Number(portStr),
      },
    });
  });

  after(async () => {
    await testEnv.cleanup();
  });

  beforeEach(async () => {
    await testEnv.clearFirestore();
    await seedBaseHouse();
  });

  it('bloqueia acesso não autenticado em todas as coleções', async () => {
    const anonDb = dbFor(null);
    await assertFails(getDoc(doc(anonDb, 'users', ADMIN_UID)));
    await assertFails(getDoc(doc(anonDb, 'houses', HOUSE_ID)));
    await assertFails(getDoc(doc(anonDb, 'houses', HOUSE_ID, 'members', ADMIN_UID)));
    await assertFails(getDoc(doc(anonDb, 'houses', HOUSE_ID, 'tasks', 'task_1')));
    await assertFails(setDoc(doc(anonDb, 'users', 'anon'), { uid: 'anon' }));
  });

  it('protege users/{uid} permitindo apenas dono ou limpeza de currentHouseId por ADMIN', async () => {
    const memberDb = dbFor(MEMBER_UID);
    const adminDb = dbFor(ADMIN_UID);
    const outsiderDb = dbFor(OUTSIDER_UID);

    await assertSucceeds(getDoc(doc(memberDb, 'users', MEMBER_UID)));
    await assertSucceeds(updateDoc(doc(memberDb, 'users', MEMBER_UID), { nome: 'Novo Nome' }));
    await assertFails(getDoc(doc(memberDb, 'users', ADMIN_UID)));
    await assertFails(updateDoc(doc(memberDb, 'users', OTHER_MEMBER_UID), { nome: 'Hack' }));
    await assertFails(deleteDoc(doc(memberDb, 'users', MEMBER_UID)));

    // ADMIN pode limpar apenas currentHouseId para null, não outros campos nem usuários de outra casa
    await assertSucceeds(updateDoc(doc(adminDb, 'users', MEMBER_UID), { currentHouseId: null }));
    await assertFails(updateDoc(doc(adminDb, 'users', OTHER_MEMBER_UID), { nome: 'Hack' }));
    await assertFails(updateDoc(doc(outsiderDb, 'users', MEMBER_UID), { currentHouseId: null }));
  });

  it('restringe houses/{houseId}: ADMIN edita tudo, MEMBER edita apenas semanaAtual válida', async () => {
    const adminDb = dbFor(ADMIN_UID);
    const memberDb = dbFor(MEMBER_UID);
    const outsiderDb = dbFor(OUTSIDER_UID);

    await assertSucceeds(getDoc(doc(outsiderDb, 'houses', HOUSE_ID)));
    await assertSucceeds(updateDoc(doc(adminDb, 'houses', HOUSE_ID), { nome: 'Casa Nova', codigoConvite: 'SV-9999' }));

    await assertFails(updateDoc(doc(memberDb, 'houses', HOUSE_ID), { nome: 'Hack' }));
    await assertFails(updateDoc(doc(memberDb, 'houses', HOUSE_ID), { semanaAtual: 'invalida' }));
    await assertFails(updateDoc(doc(memberDb, 'houses', HOUSE_ID), { semanaAtual: '2026-W46' }));
    await assertFails(deleteDoc(doc(memberDb, 'houses', HOUSE_ID)));

    await assertSucceeds(updateDoc(doc(memberDb, 'houses', HOUSE_ID), { semanaAtual: '2026-W47' }));
  });

  it('permite batch atômico de createHouse e impede auto-promoção na entrada por convite', async () => {
    const creatorDb = dbFor(OUTSIDER_UID);
    const batch = writeBatch(creatorDb);
    batch.set(doc(creatorDb, 'houses', 'house_new'), {
      id: 'house_new',
      nome: 'Nova Casa',
      codigoConvite: 'SV-5555',
      criadaPorUid: OUTSIDER_UID,
      semanaAtual: '2026-W46',
    });
    batch.set(doc(creatorDb, 'houses', 'house_new', 'members', OUTSIDER_UID), {
      uid: OUTSIDER_UID,
      nome: 'Criador',
      papel: 'ADMIN',
      pontosPorMes: { '2026-11': 0 },
      preferences: {},
    });
    batch.set(doc(creatorDb, 'houses', 'house_new', 'categories', 'limpeza'), {
      id: 'limpeza',
      nome: 'Limpeza',
      padrao: true,
    });
    batch.set(doc(creatorDb, 'users', OUTSIDER_UID), {
      uid: OUTSIDER_UID,
      nome: 'Criador',
      currentHouseId: 'house_new',
    });
    await assertSucceeds(batch.commit());

    // Entrada por convite: auto-promoção para ADMIN deve falhar; MEMBER deve passar
    const joinerDb = dbFor('uid_joiner');
    await assertFails(setDoc(doc(joinerDb, 'houses', HOUSE_ID, 'members', 'uid_joiner'), {
      uid: 'uid_joiner',
      nome: 'Invasor',
      papel: 'ADMIN',
      pontosPorMes: {},
      preferences: {},
    }));
    await assertSucceeds(setDoc(doc(joinerDb, 'houses', HOUSE_ID, 'members', 'uid_joiner'), {
      uid: 'uid_joiner',
      nome: 'Novo Morador',
      papel: 'MEMBER',
      pontosPorMes: {},
      preferences: {},
    }));
  });

  it('protege preferences, papel e saída/remoção em members/{uid}', async () => {
    const adminDb = dbFor(ADMIN_UID);
    const memberDb = dbFor(MEMBER_UID);

    await assertSucceeds(updateDoc(doc(memberDb, 'houses', HOUSE_ID, 'members', MEMBER_UID), {
      'preferences.task_1': 'GOSTO',
    }));
    await assertFails(updateDoc(doc(memberDb, 'houses', HOUSE_ID, 'members', MEMBER_UID), {
      papel: 'ADMIN',
    }));
    await assertFails(updateDoc(doc(memberDb, 'houses', HOUSE_ID, 'members', OTHER_MEMBER_UID), {
      'preferences.task_1': 'ODEIO',
    }));
    await assertFails(deleteDoc(doc(memberDb, 'houses', HOUSE_ID, 'members', OTHER_MEMBER_UID)));

    await assertSucceeds(updateDoc(doc(adminDb, 'houses', HOUSE_ID, 'members', OTHER_MEMBER_UID), {
      papel: 'ADMIN',
    }));
    await assertSucceeds(deleteDoc(doc(memberDb, 'houses', HOUSE_ID, 'members', MEMBER_UID)));
  });

  it('restringe categories e tasks a ADMIN e bloqueia delete físico em tasks fora da exclusão da casa', async () => {
    const adminDb = dbFor(ADMIN_UID);
    const memberDb = dbFor(MEMBER_UID);

    await assertSucceeds(getDoc(doc(memberDb, 'houses', HOUSE_ID, 'categories', 'limpeza')));
    await assertSucceeds(getDoc(doc(memberDb, 'houses', HOUSE_ID, 'tasks', 'task_1')));

    await assertFails(setDoc(doc(memberDb, 'houses', HOUSE_ID, 'categories', 'pets'), { id: 'pets', nome: 'Pets' }));
    await assertFails(setDoc(doc(memberDb, 'houses', HOUSE_ID, 'tasks', 'task_2'), {
      id: 'task_2',
      titulo: 'Hack',
      peso: 5,
      ativa: true,
    }));
    await assertFails(updateDoc(doc(memberDb, 'houses', HOUSE_ID, 'tasks', 'task_1'), { titulo: 'Alterado' }));
    await assertFails(deleteDoc(doc(memberDb, 'houses', HOUSE_ID, 'tasks', 'task_1')));

    // ADMIN pode criar/atualizar/soft-delete task com peso válido, mas delete físico isolado é bloqueado
    await assertFails(setDoc(doc(adminDb, 'houses', HOUSE_ID, 'tasks', 'task_invalid'), {
      id: 'task_invalid',
      titulo: 'Peso inválido',
      peso: 99,
      ativa: true,
    }));
    await assertSucceeds(updateDoc(doc(adminDb, 'houses', HOUSE_ID, 'tasks', 'task_1'), { ativa: false }));
    await assertFails(deleteDoc(doc(adminDb, 'houses', HOUSE_ID, 'tasks', 'task_1')));

    // destroyHouse: delete físico de task passa quando a casa é excluída no mesmo batch
    const destroyBatch = writeBatch(adminDb);
    destroyBatch.delete(doc(adminDb, 'houses', HOUSE_ID, 'tasks', 'task_1'));
    destroyBatch.delete(doc(adminDb, 'houses', HOUSE_ID, 'categories', 'limpeza'));
    destroyBatch.delete(doc(adminDb, 'houses', HOUSE_ID, 'members', MEMBER_UID));
    destroyBatch.delete(doc(adminDb, 'houses', HOUSE_ID, 'members', OTHER_MEMBER_UID));
    destroyBatch.delete(doc(adminDb, 'houses', HOUSE_ID, 'members', ADMIN_UID));
    destroyBatch.delete(doc(adminDb, 'houses', HOUSE_ID));
    await assertSucceeds(destroyBatch.commit());
  });

  it('valida ciclo completo de assignments: avulsa, claim/unclaim, conclusão pelo responsável, rollover e distribuição ADMIN', async () => {
    const adminDb = dbFor(ADMIN_UID);
    const memberDb = dbFor(MEMBER_UID);
    const otherDb = dbFor(OTHER_MEMBER_UID);

    // Criação avulsa por MEMBER (válida vs spoof de peso/rollover)
    await assertFails(setDoc(doc(memberDb, 'houses', HOUSE_ID, 'assignments', 'a_spoof'), {
      id: 'a_spoof',
      semana: '2026-W46',
      titulo: 'Hack pts',
      peso: 999,
      status: 'PENDENTE',
      responsavelUid: null,
      ehAvulsa: true,
      ehRollover: false,
      distribuidaAutomaticamente: false,
      criadaPorUid: MEMBER_UID,
    }));
    await assertFails(setDoc(doc(memberDb, 'houses', HOUSE_ID, 'assignments', 'a_fake_rollover'), {
      id: 'a_fake_rollover',
      semana: '2026-W46',
      titulo: 'Fake rollover',
      peso: 8,
      status: 'PENDENTE',
      responsavelUid: null,
      ehAvulsa: false,
      ehRollover: true,
      distribuidaAutomaticamente: false,
      criadaPorUid: ADMIN_UID,
    }));
    await assertSucceeds(setDoc(doc(memberDb, 'houses', HOUSE_ID, 'assignments', 'a_avulsa'), {
      id: 'a_avulsa',
      semana: '2026-W46',
      titulo: 'Comprar esponja',
      peso: 2,
      status: 'PENDENTE',
      responsavelUid: null,
      ehAvulsa: true,
      ehRollover: false,
      distribuidaAutomaticamente: false,
      criadaPorUid: MEMBER_UID,
    }));

    // Claim por MEMBER (apenas para si)
    await assertFails(updateDoc(doc(memberDb, 'houses', HOUSE_ID, 'assignments', 'a_avulsa'), {
      status: 'ATRIBUIDA',
      responsavelUid: OTHER_MEMBER_UID,
      responsavelNome: 'Other',
      distribuidaAutomaticamente: false,
    }));
    await assertSucceeds(updateDoc(doc(memberDb, 'houses', HOUSE_ID, 'assignments', 'a_avulsa'), {
      status: 'ATRIBUIDA',
      responsavelUid: MEMBER_UID,
      responsavelNome: 'Member',
      distribuidaAutomaticamente: false,
    }));

    // Conclusão e estorno: apenas o próprio responsável (ADMIN ou outro membro NÃO podem concluir/estornar)
    await assertFails(updateDoc(doc(otherDb, 'houses', HOUSE_ID, 'assignments', 'a_avulsa'), {
      status: 'CONCLUIDA',
      concluidaEm: serverTimestamp(),
    }));
    await assertFails(updateDoc(doc(adminDb, 'houses', HOUSE_ID, 'assignments', 'a_avulsa'), {
      status: 'CONCLUIDA',
      concluidaEm: serverTimestamp(),
    }));
    await assertSucceeds(updateDoc(doc(memberDb, 'houses', HOUSE_ID, 'assignments', 'a_avulsa'), {
      status: 'CONCLUIDA',
      concluidaEm: serverTimestamp(),
    }));
    await assertFails(updateDoc(doc(adminDb, 'houses', HOUSE_ID, 'assignments', 'a_avulsa'), {
      status: 'ATRIBUIDA',
      concluidaEm: null,
    }));
    await assertSucceeds(updateDoc(doc(memberDb, 'houses', HOUSE_ID, 'assignments', 'a_avulsa'), {
      status: 'ATRIBUIDA',
      concluidaEm: null,
    }));

    // Unclaim pelo próprio responsável
    await assertSucceeds(updateDoc(doc(memberDb, 'houses', HOUSE_ID, 'assignments', 'a_avulsa'), {
      status: 'PENDENTE',
      responsavelUid: null,
      responsavelNome: null,
      distribuidaAutomaticamente: false,
    }));

    // Distribuição automática: apenas ADMIN
    await assertFails(updateDoc(doc(memberDb, 'houses', HOUSE_ID, 'assignments', 'a_avulsa'), {
      status: 'ATRIBUIDA',
      responsavelUid: OTHER_MEMBER_UID,
      responsavelNome: 'Other',
      distribuidaAutomaticamente: true,
    }));
    await assertSucceeds(updateDoc(doc(adminDb, 'houses', HOUSE_ID, 'assignments', 'a_avulsa'), {
      status: 'ATRIBUIDA',
      responsavelUid: OTHER_MEMBER_UID,
      responsavelNome: 'Other',
      distribuidaAutomaticamente: true,
    }));

    // Delete isolado por MEMBER bloqueado; rollover atômico por MEMBER permitido
    await assertFails(deleteDoc(doc(memberDb, 'houses', HOUSE_ID, 'assignments', 'a_avulsa')));

    const rolloverBatch = writeBatch(memberDb);
    rolloverBatch.delete(doc(memberDb, 'houses', HOUSE_ID, 'assignments', 'a_avulsa'));
    rolloverBatch.set(doc(memberDb, 'houses', HOUSE_ID, 'assignments', '2026-W47_rollover_a_avulsa'), {
      id: '2026-W47_rollover_a_avulsa',
      semana: '2026-W47',
      titulo: 'Comprar esponja',
      peso: 2,
      status: 'PENDENTE',
      responsavelUid: null,
      ehAvulsa: true,
      ehRollover: true,
      distribuidaAutomaticamente: false,
      criadaPorUid: MEMBER_UID,
    });
    rolloverBatch.set(doc(memberDb, 'houses', HOUSE_ID, 'assignments', '2026-W47_task_task_1'), {
      id: '2026-W47_task_task_1',
      semana: '2026-W47',
      taskId: 'task_1',
      titulo: 'Lavar banheiro',
      peso: 8,
      status: 'PENDENTE',
      responsavelUid: null,
      ehAvulsa: false,
      ehRollover: false,
      distribuidaAutomaticamente: false,
      criadaPorUid: ADMIN_UID,
    });
    rolloverBatch.update(doc(memberDb, 'houses', HOUSE_ID), { semanaAtual: '2026-W47' });
    await assertSucceeds(rolloverBatch.commit());
  });
});
