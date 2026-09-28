import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, timeout } from 'rxjs';
import { Aluno } from './aluno';

export interface NovoAluno {
  ra: string;
  nome: string;
  turma: string;
  sala: string;
  periodo: string;
}

@Injectable({
  providedIn: 'root'
})
export class AlunoService {
  private readonly urlRelatorio = 'https://alunoph.com.br/api-alunos/alunos/listarAlunos.php';
  private readonly urlCadastro = 'https://alunoph.com.br/api-alunos/alunos/criarAluno.php';

  constructor(private readonly http: HttpClient) {}

  listar(): Observable<Aluno[]> {
    return this.http.get<Aluno[]>(this.urlRelatorio).pipe(timeout({ first: 10000 }));
  }

  cadastrar(aluno: NovoAluno): Observable<unknown> {
    return this.http.post(this.urlCadastro, aluno);
  }
}
