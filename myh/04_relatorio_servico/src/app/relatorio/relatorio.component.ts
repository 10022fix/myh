import { Component, OnInit } from '@angular/core';
import { Aluno } from '../aluno';
import { AlunoService } from '../aluno.service';

@Component({
  selector: 'app-relatorio',
  standalone: true,
  templateUrl: './relatorio.component.html',
  styleUrl: './relatorio.component.css'
})
export class RelatorioComponent implements OnInit {
  alunos: Aluno[] = [];
  carregando = true;
  erro = false;

  constructor(private readonly alunoService: AlunoService) {}

  ngOnInit(): void {
    this.alunoService.listar().subscribe({
      next: (alunos) => {
        this.alunos = alunos;
        this.carregando = false;
      },
      error: () => {
        this.erro = true;
        this.carregando = false;
      }
    });
  }
}
